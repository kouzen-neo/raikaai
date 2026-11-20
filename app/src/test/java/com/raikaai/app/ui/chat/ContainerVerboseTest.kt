package com.raikaai.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerVerboseTest {

    @Test
    fun runBlockShowsCommandAndOutput() {
        val block = formatContainerRunBlock(
            command = "ls /workspace",
            stdout = "a.txt\nb.txt\n",
            stderr = "",
            exitCode = 0,
            timedOut = false,
            autoApproved = false,
        )
        assertTrue(block.contains("`ls /workspace`"))
        assertTrue(block.contains("a.txt"))
        assertTrue(block.contains("exit=0"))
    }

    @Test
    fun runBlockMarksAutoApprovedAndStderr() {
        val block = formatContainerRunBlock(
            command = "cat x",
            stdout = "",
            stderr = "No such file",
            exitCode = 1,
            timedOut = false,
            autoApproved = true,
        )
        assertTrue(block.contains("auto"))
        assertTrue(block.contains("No such file"))
        assertTrue(block.contains("exit=1"))
    }

    @Test
    fun runBlockStaysCapped() {
        val block = formatContainerRunBlock(
            command = "yes",
            stdout = "y\n".repeat(5000),
            stderr = "e\n".repeat(2000),
            exitCode = 0,
            timedOut = true,
            autoApproved = false,
        )
        assertTrue(block.length <= 1600)
        assertTrue(block.contains("timed out"))
        // The closing fence must survive capping.
        assertTrue(block.trimEnd().endsWith("```"))
    }

    @Test
    fun resultReplacesRunningCardEvenWithShellSymbols() {
        val cmd = "echo '--- OS ---'; which node || echo 'none' && ls -la /workspace"
        val call = "<tool_call>{\"name\":\"execute_in_container\"}</tool_call>"
        val marker = pendingToolCard(cmd)
        val running = "Checking.\n$call\nmore".replaceFirstLiteral(call, marker)
        val block = formatContainerRunBlock(cmd, "ok", "", 0, false, false)
        val done = swapInToolResult(running, marker, call, block)
        // The running card is gone and the result appears exactly once.
        assertTrue(!done.contains("pending"))
        assertEquals(1, Regex("exit=0").findAll(done).count())
    }

    @Test
    fun identicalCallsAreSwappedOneAtATime() {
        val call = "<tool_call>{\"x\":1}</tool_call>"
        val twice = "$call\n$call"
        assertEquals("A\n$call", twice.replaceFirstLiteral(call, "A"))
    }

    @Test
    fun runBlockKeepsFenceWithLongCommand() {
        val block = formatContainerRunBlock(
            command = "cat > app.py <<'EOF'\n" + "print('x')\n".repeat(400) + "EOF",
            stdout = "y\n".repeat(5000),
            stderr = "e\n".repeat(2000),
            exitCode = 0,
            timedOut = false,
            autoApproved = false,
        )
        assertTrue(block.trimEnd().endsWith("```"))
        // Only the first command line is shown.
        assertTrue(block.contains("`cat > app.py <<'EOF' …`"))
        assertTrue(!block.contains("print('x')"))
    }
}
