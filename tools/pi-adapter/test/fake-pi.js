// Fake Pi RPC child process used by tests. Reads JSONL commands on stdin and
// writes JSONL responses/events on stdout. No real Pi installation required.

import { createJsonlDecoder, encodeJsonl } from "../src/framing.js";

const decoder = createJsonlDecoder((command) => {
  const id = command.id;

  function respond(data = {}) {
    process.stdout.write(encodeJsonl({
      id,
      type: "response",
      command: command.type,
      success: true,
      data,
    }));
  }

  if (command.type === "prompt") {
    respond({ disposition: "started" });
    // U+2028 is a valid JSON string character but node:readline would split
    // on it. It must survive framing intact.
    process.stdout.write(encodeJsonl({
      type: "message_update",
      assistantMessageEvent: {
        type: "text_delta",
        contentIndex: 0,
        delta: `Hello\u2028world\u2029!`,
      },
    }));
    process.stdout.write(encodeJsonl({
      type: "tool_execution_start",
      toolCallId: "call_fake_1",
      toolName: "web_search",
      args: { query: "pi rpc" },
    }));
    process.stdout.write(encodeJsonl({
      type: "approval_request",
      id: "approval_fake_1",
      toolName: "bash",
      command: "echo hi",
    }));
    process.stdout.write(encodeJsonl({
      type: "turn_end",
      message: { role: "assistant", content: "Hello world!" },
      toolResults: [],
    }));
    process.stdout.write(encodeJsonl({ type: "agent_settled", aborted: false }));
    return;
  }

  if (command.type === "abort") {
    // Pi's abort response has no data payload.
    process.stdout.write(encodeJsonl({
      id,
      type: "response",
      command: "abort",
      success: true,
    }));
    process.stdout.write(encodeJsonl({ type: "agent_settled", aborted: true }));
    return;
  }

  respond();
}, (error, line) => {
  process.stderr.write(`fake-pi: bad command ${line}: ${error.message}\n`);
});

process.stdin.on("data", (chunk) => decoder.write(chunk));
process.stdin.on("end", () => decoder.end());
process.stderr.write("fake-pi ready\n");
