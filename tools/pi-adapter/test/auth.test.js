import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { PiClient } from "../src/pi-client.js";
import { createAdapter } from "../src/server.js";
import { startFakeUpstream } from "./fake-upstream.js";

const fakePi = fileURLToPath(new URL("./fake-pi.js", import.meta.url));

test("adapter token guards every endpoint except /health", async (t) => {
  const adapter = createAdapter({
    token: "secret-token",
    piClient: new PiClient({ bin: [process.execPath, fakePi] }),
    upstreamBaseUrl: "http://127.0.0.1:1/v1",
  });
  t.after(() => adapter.close());
  const server = adapter.server;
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;

  const health = await fetch(`${base}/health`);
  assert.equal(health.status, 200);

  const anonymous = await fetch(`${base}/events`);
  assert.equal(anonymous.status, 401);

  const wrong = await fetch(`${base}/events`, {
    headers: { authorization: "Bearer wrong-token" },
  });
  assert.equal(wrong.status, 401);

  const authorized = await fetch(`${base}/events`, {
    headers: { authorization: "Bearer secret-token" },
  });
  assert.equal(authorized.status, 200);
  assert.ok(Array.isArray(await authorized.json()));

  const aborted = await fetch(`${base}/v1/abort`, {
    method: "POST",
    headers: { authorization: "Bearer secret-token" },
  });
  assert.equal(aborted.status, 200);
});

test("adapter token is not relayed; upstream uses its own token", async (t) => {
  const upstream = await startFakeUpstream({ toolText: "plain answer" });
  t.after(() => upstream.close());
  const adapter = createAdapter({
    token: "adapter-token",
    upstreamToken: "upstream-token",
    upstreamBaseUrl: upstream.url,
    piClient: new PiClient({ bin: [process.execPath, fakePi] }),
  });
  t.after(() => adapter.close());
  const server = adapter.server;
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));

  const response = await fetch(`http://127.0.0.1:${server.address().port}/v1/chat/completions`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: "Bearer adapter-token",
    },
    body: JSON.stringify({ model: "m", messages: [{ role: "user", content: "hi" }] }),
  });
  assert.equal(response.status, 200);
  assert.deepEqual(upstream.authorizations, ["Bearer upstream-token"]);
});
