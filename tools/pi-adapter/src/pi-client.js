// Thin Node wrapper around `pi --mode rpc --no-session`.
// Commands are correlated by `id`; events are emitted as they arrive on stdout.
// stderr is treated as diagnostics only and is never parsed as protocol data.

import { spawn } from "node:child_process";
import { EventEmitter, once } from "node:events";
import { createJsonlDecoder, encodeJsonl } from "./framing.js";

const DEFAULT_ARGS = ["--mode", "rpc", "--no-session"];

function defaultLogger() {}

export class PiClient extends EventEmitter {
  /**
   * @param {object} options
   * @param {string|string[]} options.bin  Executable (and optional leading
   *   arguments). Defaults to PI_BIN. May be an argv array when the path
   *   contains spaces. The fixed `--mode rpc --no-session` args are appended.
   * @param {string[]} options.args
   * @param {(message: string) => void} options.logger
   */
  constructor({
    bin = process.env.PI_BIN || "pi",
    args = DEFAULT_ARGS,
    logger = defaultLogger,
  } = {}) {
    super();
    this.logger = typeof logger === "function" ? logger : defaultLogger;
    this.pending = new Map();
    this.nextId = 1;
    this.closed = false;
    this.diagnostics = [];

    const parts = Array.isArray(bin) ? bin.slice() : String(bin).trim().split(/\s+/);
    if (parts.length === 0) parts.push("pi");
    const command = parts.shift();
    this.argv = [...parts, ...args];
    this.child = spawn(command, this.argv, {
      stdio: ["pipe", "pipe", "pipe"],
    });

    // Install the stdout subscriber BEFORE any command can be sent, so a fast
    // completion cannot be missed (Pi RPC requirement: subscribe first).
    this.decoder = createJsonlDecoder(
      (record) => this._onRecord(record),
      (error, line) => {
        const diagnostic = { kind: "parse", error: error.message, line };
        this.diagnostics.push(diagnostic);
        this.logger(`pi-adapter: malformed JSONL record: ${error.message}`);
        this._emit("diagnostic", diagnostic);
      },
    );
    this.child.stdout.on("data", (chunk) => this.decoder.write(chunk));
    this.child.stdout.on("end", () => this.decoder.end());

    this.child.stderr.on("data", (chunk) => {
      const text = chunk.toString("utf8");
      const diagnostic = { kind: "stderr", text };
      this.diagnostics.push(diagnostic);
      this.logger(`pi stderr: ${text.trimEnd()}`);
      this._emit("diagnostic", diagnostic);
    });

    this.child.on("error", (error) => {
      this._failPending(error);
      this._emit("error", error);
    });
    this.child.on("exit", (code, signal) => {
      this.closed = true;
      const error = new Error(`Pi process exited (code=${code}, signal=${signal})`);
      this._failPending(error);
      this._emit("exit", { code, signal });
    });
  }

  // EventEmitter throws when "error" is emitted without a listener. Pi
  // startup failures should not crash the adapter, so guard that case.
  _emit(name, value) {
    if (name === "error" && this.listenerCount("error") === 0) {
      this.logger(`pi-adapter: ${value && value.message ? value.message : value}`);
      return;
    }
    this.emit(name, value);
  }

  _failPending(error) {
    for (const { reject } of this.pending.values()) reject(error);
    this.pending.clear();
  }

  _onRecord(record) {
    this.emit("record", record);
    if (record && record.type === "response") {
      const entry = this.pending.get(record.id);
      if (entry) {
        this.pending.delete(record.id);
        if (record.success) entry.resolve(record.data);
        else entry.reject(new Error(record.error || "Pi command failed"));
      }
      return;
    }
    this.emit("event", record);
    if (record && typeof record.type === "string") this.emit(record.type, record);
    if (record && record.type === "agent_settled") this.emit("settled", record);
  }

  /** Send a command and resolve with its response data. */
  send(type, extra = {}) {
    if (this.closed) return Promise.reject(new Error("PiClient is closed"));
    const id = `cmd-${this.nextId++}`;
    const record = { id, type, ...extra };

    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      const line = encodeJsonl(record);
      this.emit("command", record);
      const ok = this.child.stdin.write(line);
      if (!ok) {
        // Honor stdin backpressure instead of buffering without bound.
        once(this.child.stdin, "drain").catch(() => {});
      }
    });
  }

  prompt(message, extra = {}) {
    return this.send("prompt", { message, ...extra });
  }

  abort() {
    return this.send("abort");
  }

  /**
   * Subscribe to `agent_settled` BEFORE sending the prompt, then resolve when
   * Pi settles. `agent_end` is not enough: retries, compaction, steering and
   * follow-ups may continue after it. The default 10-minute deadline allows a
   * long multi-round agent turn; callers can override with `timeoutMs`.
   */
  promptAndWait(message, extra = {}) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        cleanup();
        reject(new Error("Timed out waiting for agent_settled"));
      }, extra.timeoutMs ?? 600000);
      const { timeoutMs: _timeout, ...promptExtra } = extra;

      const cleanup = () => {
        clearTimeout(timer);
        this.off("settled", onSettled);
        this.off("error", onError);
      };
      const onSettled = (settled) => {
        cleanup();
        resolve({ accepted: true, settled });
      };
      const onError = (error) => {
        cleanup();
        reject(error);
      };

      this.on("settled", onSettled);
      this.on("error", onError);
      this.prompt(message, promptExtra).catch(onError);
    });
  }

  /** Request an orderly shutdown (close stdin; Pi disposes the runtime). */
  close() {
    if (this.closed) return;
    this.closed = true;
    try {
      this.child.stdin.end();
    } catch {
      // already ended
    }
  }

  /** Force-stop the child (tests / abnormal shutdown). */
  kill() {
    try {
      this.child.kill("SIGTERM");
    } catch {
      // already gone
    }
  }
}
