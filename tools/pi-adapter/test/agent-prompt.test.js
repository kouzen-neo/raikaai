import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { PiClient } from "../src/pi-client.js";
import { createAdapter } from "../src/server.js";

const fakePi = fileURLToPath(new URL("./fake-pi.js", import.meta.url));

function createTestAdapter(t) {
  const adapter = createAdapter({
    token: "t",
    piClient: new PiClient({ bin: [process.execPath, fakePi] }),
    upstreamBaseUrl: "http://127.0.0.1:1/v1",
  });
  t.after(() => adapter.close());
  return adapter;
}

test("POST /v1/agent/prompt accepts an OpenAI-shaped messages array", async (t) => {
  const adapter = createTestAdapter(t);
  const server = adapter.server;
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;

  const response = await fetch(`${base}/v1/agent/prompt`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer t" },
    body: JSON.stringify({
      messages: [
        { role: "system", content: "be brief" },
        { role: "user", content: "go" },
      ],
    }),
  });
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ok, true);
  assert.equal(body.settled.aborted, false);
});

test("GET /events?since returns only newer events", async (t) => {
  const adapter = createTestAdapter(t);
  const server = adapter.server;
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;

  await fetch(`${base}/v1/agent/prompt`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer t" },
    body: JSON.stringify({ message: "go" }),
  });
  await new Promise((resolve) => setTimeout(resolve, 300));

  const all = await (await fetch(`${base}/events`, {
    headers: { authorization: "Bearer t" },
  })).json();
  assert.ok(all.length > 0);
  const newestSeq = Math.max(...all.map((event) => event.seq));

  const newer = await (await fetch(`${base}/events?since=${newestSeq}`, {
    headers: { authorization: "Bearer t" },
  })).json();
  assert.deepEqual(newer, []);
});
