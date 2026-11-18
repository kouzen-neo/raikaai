import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { PiClient } from "../src/pi-client.js";
import { EventTranslator } from "../src/event-translator.js";
import { createAdapter } from "../src/server.js";

const fakePi = fileURLToPath(new URL("./fake-pi.js", import.meta.url));

function waitForSettled(pi, timeoutMs = 3000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("no agent_settled")), timeoutMs);
    pi.on("settled", (record) => {
      clearTimeout(timer);
      resolve(record);
    });
  });
}

test("EventTranslator maps Pi events to the host event vocabulary", async (t) => {
  const pi = new PiClient({ bin: [process.execPath, fakePi] });
  t.after(() => {
    pi.close();
    pi.kill();
  });
  const translator = new EventTranslator();
  translator.attach(pi);

  const settled = waitForSettled(pi); // subscribe before prompting
  await pi.prompt("go");
  await settled;

  const events = translator.snapshot();
  const ofType = (type) => events.filter((event) => event.type === type);

  const deltas = ofType("assistant-stream");
  assert.equal(deltas.length, 1);
  assert.equal(deltas[0].delta, "Hello\u2028world\u2029!");

  const calls = ofType("tool/call");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].name, "web_search");
  assert.equal(calls[0].id, "call_fake_1");
  assert.deepEqual(calls[0].args, { query: "pi rpc" });

  const approvals = ofType("approval/request");
  assert.equal(approvals.length, 1);
  assert.equal(approvals[0].id, "approval_fake_1");

  const turnEnds = ofType("turn/end");
  assert.equal(turnEnds.length, 1);
  assert.equal(turnEnds[0].message.role, "assistant");

  assert.equal(ofType("settled")[0].aborted, false);

  let emitted = 0;
  translator.on("translated", () => { emitted += 1; });
  translator.translate({ type: "message_update", assistantMessageEvent: { type: "thinking_delta", delta: "x" } });
  assert.equal(emitted, 0, "unrelated events must not be translated");
});

test("GET /events exposes the translated snapshot over HTTP", async (t) => {
  const adapter = createAdapter({
    piClient: new PiClient({ bin: [process.execPath, fakePi] }),
    upstreamBaseUrl: "http://127.0.0.1:1/v1",
  });
  t.after(() => adapter.close());
  const server = adapter.server;
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));

  const settled = waitForSettled(adapter.piClient); // subscribe before prompting
  await adapter.piClient.prompt("go");
  await settled;

  const health = await fetch(`http://127.0.0.1:${server.address().port}/health`);
  assert.deepEqual(await health.json(), { ok: true });

  const response = await fetch(`http://127.0.0.1:${server.address().port}/events`);
  assert.equal(response.status, 200);
  const events = await response.json();
  assert.ok(Array.isArray(events));
  assert.ok(events.some((event) => event.type === "tool/call"));
  assert.ok(events.some((event) => event.type === "approval/request"));
  assert.ok(events.some((event) => event.type === "turn/end"));
});
