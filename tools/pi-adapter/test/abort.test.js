import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { PiClient } from "../src/pi-client.js";
import { createAdapter } from "../src/server.js";

const fakePi = fileURLToPath(new URL("./fake-pi.js", import.meta.url));

test("PiClient.abort correlates its response and observes the aborted settle", async (t) => {
  const pi = new PiClient({ bin: [process.execPath, fakePi] });
  t.after(() => {
    pi.close();
    pi.kill();
  });

  // Subscribe before prompting. The prompt emits its own (non-aborted) settle
  // synchronously; only the settle caused by abort has aborted: true.
  const abortedSettle = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("no aborted settle")), 3000);
    pi.on("settled", (record) => {
      if (record.aborted) {
        clearTimeout(timer);
        resolve(record);
      }
    });
  });

  await pi.prompt("long running task");
  const result = await pi.abort();
  assert.equal(result, undefined, "abort responds with no data");
  assert.equal((await abortedSettle).aborted, true);
});

test("POST /v1/abort relays abort to Pi and reports success", async (t) => {
  const adapter = createAdapter({
    piClient: new PiClient({ bin: [process.execPath, fakePi] }),
    upstreamBaseUrl: "http://127.0.0.1:1/v1",
  });
  t.after(() => adapter.close());
  const server = adapter.server;
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const port = server.address().port;

  const settled = new Promise((resolve) => adapter.piClient.on("settled", resolve));
  const response = await fetch(`http://127.0.0.1:${port}/v1/abort`, { method: "POST" });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, aborted: true });
  assert.equal((await settled).aborted, true);
});

test("abort before any prompt still succeeds", async (t) => {
  const pi = new PiClient({ bin: [process.execPath, fakePi] });
  t.after(() => {
    pi.close();
    pi.kill();
  });
  await pi.abort();
  assert.equal(pi.pending.size, 0, "no commands remain outstanding");
});
