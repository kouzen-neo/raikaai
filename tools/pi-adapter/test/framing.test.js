import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { createJsonlDecoder, encodeJsonl } from "../src/framing.js";
import { PiClient } from "../src/pi-client.js";

const fakePi = fileURLToPath(new URL("./fake-pi.js", import.meta.url));

test("decoder splits only on LF and preserves U+2028/U+2029 in JSON strings", () => {
  const records = [];
  const errors = [];
  const decoder = createJsonlDecoder(
    (record) => records.push(record),
    (error) => errors.push(error),
  );

  const first = { type: "message_update", delta: "line\u2028separator\u2029paragraph" };
  const second = { type: "agent_settled", aborted: false };
  const wire = `${encodeJsonl(first).replace(/\n$/, "\r\n")}${encodeJsonl(second)}`;

  decoder.write(wire);

  assert.equal(errors.length, 0);
  assert.equal(records.length, 2);
  assert.equal(records[0].delta, "line\u2028separator\u2029paragraph");
  assert.deepEqual(records[1], second);
});

test("decoder handles UTF-8 characters split across chunk boundaries", () => {
  const records = [];
  const decoder = createJsonlDecoder((record) => records.push(record));
  const wire = Buffer.from(encodeJsonl({ type: "x", text: "café ☃" }), "utf8");
  decoder.write(wire.subarray(0, wire.length - 3));
  decoder.write(wire.subarray(wire.length - 3));
  assert.equal(records.length, 1);
  assert.equal(records[0].text, "café ☃");
});

test("PiClient reads a U+2028 delta from the fake Pi without splitting the record", async (t) => {
  const pi = new PiClient({ bin: [process.execPath, fakePi] });
  t.after(() => {
    pi.close();
    pi.kill();
  });

  const records = [];
  pi.on("event", (record) => records.push(record));

  // Subscribe before prompting: a fast Pi can settle before prompt() resolves.
  const settled = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("no agent_settled")), 3000);
    pi.on("settled", () => {
      clearTimeout(timer);
      resolve();
    });
  });
  await pi.prompt("hello");
  await settled;

  const update = records.find(
    (record) => record.type === "message_update" &&
      record.assistantMessageEvent?.type === "text_delta",
  );
  assert.ok(update, "expected a message_update/text_delta event");
  assert.equal(update.assistantMessageEvent.delta, "Hello\u2028world\u2029!");
});
