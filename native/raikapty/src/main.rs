//! raikapty: run a command on a pseudo-terminal and relay it over pipes.
//!
//! The app cannot allocate a PTY from Kotlin, and the Alpine guest's busybox
//! has no `script`. This helper runs on the host, outside proot:
//!
//!   libraikapty.so ROWS COLS PROGRAM [ARGS...]
//!
//! It opens /dev/ptmx, starts PROGRAM in a new session with the PTY slave as
//! its controlling terminal and stdio, then copies stdin -> PTY master and
//! PTY master -> stdout until the program exits. Its exit code is the
//! program's (128 + signal when killed).
//!
//! Resize is in-band on stdin: the app writes `ESC ] 9999 ; ROWS ; COLS BEL`
//! as one write. The helper applies it with TIOCSWINSZ (the kernel then sends
//! SIGWINCH to the foreground job) and does not forward it.

use std::ffi::CString;
use std::os::raw::c_char;

const RESIZE_PREFIX: &[u8] = b"\x1b]9999;";

fn die(msg: &str) -> ! {
    let line = format!("raikapty: {msg}: {}\n", std::io::Error::last_os_error());
    unsafe {
        libc::write(2, line.as_ptr().cast(), line.len());
        libc::_exit(127);
    }
}

fn set_size(fd: i32, rows: u16, cols: u16) {
    let ws = libc::winsize { ws_row: rows.max(1), ws_col: cols.max(1), ws_xpixel: 0, ws_ypixel: 0 };
    unsafe {
        libc::ioctl(fd, libc::TIOCSWINSZ, &ws);
    }
}

fn write_all(fd: i32, mut data: &[u8]) -> bool {
    while !data.is_empty() {
        let n = unsafe { libc::write(fd, data.as_ptr().cast(), data.len()) };
        if n < 0 {
            if std::io::Error::last_os_error().raw_os_error() == Some(libc::EINTR) {
                continue;
            }
            return false;
        }
        data = &data[n as usize..];
    }
    true
}

/// Forwards stdin bytes to the master, applying (and removing) resize messages.
fn forward_input(master: i32, buf: &[u8]) {
    let mut rest = buf;
    while let Some(pos) = find(rest, RESIZE_PREFIX) {
        write_all(master, &rest[..pos]);
        let after = &rest[pos + RESIZE_PREFIX.len()..];
        match after.iter().position(|&b| b == 0x07) {
            Some(end) => {
                let spec = String::from_utf8_lossy(&after[..end]);
                let mut it = spec.split(';').filter_map(|v| v.trim().parse::<u16>().ok());
                if let (Some(r), Some(c)) = (it.next(), it.next()) {
                    set_size(master, r, c);
                }
                rest = &after[end + 1..];
            }
            None => {
                // Incomplete message: forward as-is rather than swallow input.
                write_all(master, &rest[pos..]);
                return;
            }
        }
    }
    write_all(master, rest);
}

fn find(hay: &[u8], needle: &[u8]) -> Option<usize> {
    hay.windows(needle.len()).position(|w| w == needle)
}

fn exit_code(status: i32) -> i32 {
    if libc::WIFEXITED(status) {
        libc::WEXITSTATUS(status)
    } else if libc::WIFSIGNALED(status) {
        128 + libc::WTERMSIG(status)
    } else {
        1
    }
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 4 {
        eprintln!("usage: raikapty ROWS COLS PROGRAM [ARGS...]");
        std::process::exit(2);
    }
    let rows: u16 = args[1].parse().unwrap_or(24);
    let cols: u16 = args[2].parse().unwrap_or(80);

    // Build exec arguments before fork: no allocation in the child.
    let c_args: Vec<CString> = args[3..]
        .iter()
        .map(|a| CString::new(a.as_bytes()).unwrap_or_else(|_| die("argument contains NUL")))
        .collect();
    let mut argv: Vec<*const c_char> = c_args.iter().map(|a| a.as_ptr()).collect();
    argv.push(std::ptr::null());

    unsafe {
        let master = libc::open(c"/dev/ptmx".as_ptr(), libc::O_RDWR | libc::O_NOCTTY | libc::O_CLOEXEC);
        if master < 0 {
            die("open /dev/ptmx");
        }
        if libc::grantpt(master) != 0 || libc::unlockpt(master) != 0 {
            die("unlock pty");
        }
        let mut name = [0 as c_char; 128];
        if libc::ptsname_r(master, name.as_mut_ptr(), name.len()) != 0 {
            die("ptsname");
        }
        set_size(master, rows, cols);

        let pid = libc::fork();
        if pid < 0 {
            die("fork");
        }
        if pid == 0 {
            // Child: new session, slave becomes the controlling terminal.
            libc::setsid();
            let slave = libc::open(name.as_ptr(), libc::O_RDWR);
            if slave < 0 {
                libc::_exit(126);
            }
            libc::ioctl(slave, libc::TIOCSCTTY, 0);
            libc::dup2(slave, 0);
            libc::dup2(slave, 1);
            libc::dup2(slave, 2);
            if slave > 2 {
                libc::close(slave);
            }
            for sig in [libc::SIGINT, libc::SIGQUIT, libc::SIGTSTP, libc::SIGTTIN, libc::SIGTTOU, libc::SIGCHLD, libc::SIGPIPE] {
                libc::signal(sig, libc::SIG_DFL);
            }
            libc::execvp(argv[0], argv.as_ptr());
            libc::_exit(127);
        }

        libc::signal(libc::SIGPIPE, libc::SIG_IGN);
        let mut buf = vec![0u8; 16384];
        let mut stdin_open = true;
        let mut status: i32 = 0;
        let mut child_done = false;
        loop {
            let mut fds = [
                libc::pollfd { fd: master, events: libc::POLLIN, revents: 0 },
                libc::pollfd { fd: if stdin_open { 0 } else { -1 }, events: libc::POLLIN, revents: 0 },
            ];
            let n = libc::poll(fds.as_mut_ptr(), 2, 200);
            if n < 0 && std::io::Error::last_os_error().raw_os_error() != Some(libc::EINTR) {
                break;
            }
            if fds[0].revents & (libc::POLLIN | libc::POLLHUP | libc::POLLERR) != 0 {
                let r = libc::read(master, buf.as_mut_ptr().cast(), buf.len());
                if r > 0 {
                    if !write_all(1, &buf[..r as usize]) {
                        break; // the app went away
                    }
                } else if r == 0 || std::io::Error::last_os_error().raw_os_error() == Some(libc::EIO) {
                    // EIO: every slave fd closed, i.e. the program and its children are gone.
                    break;
                }
            }
            if stdin_open && fds[1].revents & (libc::POLLIN | libc::POLLHUP | libc::POLLERR) != 0 {
                let r = libc::read(0, buf.as_mut_ptr().cast(), buf.len());
                if r > 0 {
                    let data = buf[..r as usize].to_vec();
                    forward_input(master, &data);
                } else {
                    stdin_open = false;
                }
            }
            if !child_done && libc::waitpid(pid, &mut status, libc::WNOHANG) == pid {
                child_done = true;
                // Drain what is left, then stop even if a background job keeps the slave open.
                loop {
                    let mut pfd = libc::pollfd { fd: master, events: libc::POLLIN, revents: 0 };
                    if libc::poll(&mut pfd, 1, 50) <= 0 {
                        break;
                    }
                    let r = libc::read(master, buf.as_mut_ptr().cast(), buf.len());
                    if r <= 0 || !write_all(1, &buf[..r as usize]) {
                        break;
                    }
                }
                break;
            }
        }
        if !child_done {
            libc::kill(-pid, libc::SIGHUP);
            libc::waitpid(pid, &mut status, 0);
        }
        libc::_exit(exit_code(status));
    }
}
