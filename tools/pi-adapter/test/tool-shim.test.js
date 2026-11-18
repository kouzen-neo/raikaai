import test from "node:test";
import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import {
  injectToolSchemas,
  parseToolCalls,
  applyToolShimToResponse,
  buildShimmedSse,
} from "../src/tool-shim.js";
import { createAdapter } from "../src/server.js";
import { startFakeUpstream } from "./fake-upstream.js";

const TOOLS = [{
  type: "function",
  function: {
    name: "get_weather",
    description: "Get the weather for a city",
    parameters: {
      type: "object",
      properties: { city: { type: "string" } },
      required: ["city"],
    },
  },
}];

class StubPi extends EventEmitter {
  async send() {}
  async prompt() {}
  async abort() {}
  close() {}
  kill() {}
}

test("injectToolSchemas moves tools into the system prompt and strips the tools field", () => {
  const forwarded = injectToolSchemas({
    model: "m",
    messages: [
      { role: "system", content: "Be brief." },
      { role: "user", content: "Weather in Paris?" },
    ],
    tools: TOOLS,
    tool_choice: "auto",
  });

  assert.equal("tools" in forwarded, false);
  assert.equal("tool_choice" in forwarded, false);
  assert.match(forwarded.messages[0].content, /Be brief\./);
  assert.match(forwarded.messages[0].content, /TOOL CALLING PROTOCOL/);
  assert.match(forwarded.messages[0].content, /"tool_calls"/);
  assert.match(forwarded.messages[0].content, /get_weather/);
  assert.deepEqual(forwarded.messages[1], { role: "user", content: "Weather in Paris?" });
});

test("injectToolSchemas adds a system message when none exists", () => {
  const forwarded = injectToolSchemas({
    messages: [{ role: "user", content: "hi" }],
    tools: TOOLS,
  });
  assert.equal(forwarded.messages.length, 2);
  assert.equal(forwarded.messages[0].role, "system");
});

test("parseToolCalls round-trips a fenced tool object and strips it from text", () => {
  const text = 'Here you go:\n```json\n{"tool_calls":[{"name":"get_weather","arguments":{"city":"Paris"}}]}\n```\nDone.';
  const parsed = parseToolCalls(text);
  assert.ok(parsed);
  assert.equal(parsed.toolCalls.length, 1);
  assert.equal(parsed.toolCalls[0].name, "get_weather");
  assert.deepEqual(parsed.toolCalls[0].arguments, { city: "Paris" });
  assert.equal(parsed.visibleText, "Here you go:\nDone.");
});

test("parseToolCalls accepts a bare object and an OpenAI-shaped tool call", () => {
  const bare = parseToolCalls('{"tool_calls":[{"name":"lookup","arguments":{"q":"x"}}]}');
  assert.equal(bare.visibleText, "");
  assert.equal(bare.toolCalls[0].name, "lookup");

  const shaped = parseToolCalls('Intro {"tool_calls":[{"type":"function","function":{"name":"f","arguments":"{\\"a\\":1}"}}]} outro');
  assert.equal(shaped.toolCalls[0].name, "f");
  assert.deepEqual(shaped.toolCalls[0].arguments, { a: 1 });
  assert.equal(shaped.visibleText, "Intro  outro");
});

test("parseToolCalls returns null for plain prose", () => {
  assert.equal(parseToolCalls("Just a normal answer."), null);
});

test("applyToolShimToResponse produces OpenAI tool_calls and finish_reason", () => {
  const response = {
    choices: [{
      index: 0,
      message: { role: "assistant", content: '{"tool_calls":[{"name":"get_weather","arguments":{"city":"Paris"}}]}' },
      finish_reason: "stop",
    }],
  };
  const result = applyToolShimToResponse(response);
  const message = result.choices[0].message;
  assert.equal(result.choices[0].finish_reason, "tool_calls");
  assert.equal(message.content, "");
  assert.equal(message.tool_calls.length, 1);
  assert.match(message.tool_calls[0].id, /^call_/);
  assert.equal(message.tool_calls[0].type, "function");
  assert.equal(message.tool_calls[0].function.name, "get_weather");
  assert.deepEqual(JSON.parse(message.tool_calls[0].function.arguments), { city: "Paris" });
});

test("non-stream POST /v1/chat/completions injects tools and returns tool_calls", async (t) => {
  const upstream = await startFakeUpstream();
  t.after(() => upstream.close());

  const adapter = createAdapter({
    upstreamBaseUrl: upstream.url,
    piClient: new StubPi(),
  });
  const server = adapter.server;
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());

  const port = server.address().port;
  const response = await fetch(`http://127.0.0.1:${port}/v1/chat/completions`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      model: "fake-model",
      messages: [{ role: "user", content: "Weather in Paris?" }],
      tools: TOOLS,
    }),
  });
  assert.equal(response.status, 200);
  const json = await response.json();

  assert.equal(upstream.requests.length, 1);
  assert.equal("tools" in upstream.requests[0], false);
  assert.match(upstream.requests[0].messages[0].content, /get_weather/);
  assert.equal(json.choices[0].finish_reason, "tool_calls");
  assert.equal(json.choices[0].message.tool_calls[0].function.name, "get_weather");
  assert.deepEqual(JSON.parse(json.choices[0].message.tool_calls[0].function.arguments), {
    city: "Paris",
    unit: "celsius",
  });
  assert.equal(json.choices[0].message.content.trim(), "Sure!");
});

test("stream POST /v1/chat/completions emits SSE tool_call fragments", async (t) => {
  const upstream = await startFakeUpstream();
  t.after(() => upstream.close());

  const adapter = createAdapter({
    upstreamBaseUrl: upstream.url,
    piClient: new StubPi(),
  });
  const server = adapter.server;
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());

  const port = server.address().port;
  const response = await fetch(`http://127.0.0.1:${port}/v1/chat/completions`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      model: "fake-model",
      stream: true,
      messages: [{ role: "user", content: "Weather?" }],
      tools: TOOLS,
    }),
  });
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /text\/event-stream/);
  const text = await response.text();
  assert.match(text, /^data: /);
  assert.ok(text.trimEnd().endsWith("data: [DONE]"));

  const dataLines = text.split("\n").filter((line) => line.startsWith("data: ") && !line.includes("[DONE]"));
  const chunks = dataLines.map((line) => JSON.parse(line.slice(6)));
  const names = chunks.flatMap((chunk) => chunk.choices[0].delta.tool_calls || [])
    .filter((call) => call.function && call.function.name)
    .map((call) => call.function.name);
  assert.deepEqual(names, ["get_weather"]);
  assert.ok(chunks.some((chunk) => chunk.choices[0].finish_reason === "tool_calls"));

  const visible = chunks.map((chunk) => chunk.choices[0].delta.content || "").join("");
  assert.equal(visible.trim(), "Sure!");
});

test("buildShimmedSse falls back to normal text when no tool call is present", () => {
  const sse = buildShimmedSse({ id: "x", model: "m", text: "Hello" });
  const chunks = sse.split("\n").filter((line) => line.startsWith("data: ") && !line.includes("[DONE]"))
    .map((line) => JSON.parse(line.slice(6)));
  assert.ok(chunks.some((chunk) => chunk.choices[0].delta.content === "Hello"));
  assert.ok(chunks.some((chunk) => chunk.choices[0].finish_reason === "stop"));
});
