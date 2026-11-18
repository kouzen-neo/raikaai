// Translate Pi RPC session events into the small, stable event vocabulary the
// Kotlin host consumes. The current state is exposed as a bounded JSON array
// snapshot on GET /events.

import { EventEmitter } from "node:events";

export class EventTranslator extends EventEmitter {
  constructor({ capacity = 1000 } = {}) {
    super();
    this.capacity = capacity;
    this.events = [];
    this.sequence = 0;
  }

  record(type, data = {}) {
    const event = { type, at: Date.now(), seq: ++this.sequence, ...data };
    this.events.push(event);
    if (this.events.length > this.capacity) {
      this.events.splice(0, this.events.length - this.capacity);
    }
    this.emit("translated", event);
    this.emit(type, event);
    return event;
  }

  /** Translate one raw Pi record. Returns the emitted event or null. */
  translate(record) {
    if (!record || typeof record !== "object") return null;
    const type = record.type;

    if (type === "message_update") {
      const update = record.assistantMessageEvent;
      if (update && update.type === "text_delta") {
        return this.record("assistant-stream", {
          delta: update.delta ?? "",
          contentIndex: update.contentIndex ?? 0,
        });
      }
      return null;
    }

    if (
      type === "tool_execution_start" ||
      type === "tool_call" ||
      type === "tool/call"
    ) {
      return this.record("tool/call", {
        id: record.toolCallId ?? record.id ?? null,
        name: record.toolName ?? record.name ?? null,
        args: record.args ?? {},
      });
    }

    if (
      type === "approval_request" ||
      type === "approval/request" ||
      type === "permission_request"
    ) {
      return this.record("approval/request", {
        id: record.id ?? record.approvalId ?? null,
        request: record,
      });
    }

    if (type === "turn_end" || type === "turn/end") {
      return this.record("turn/end", {
        message: record.message ?? null,
        toolResults: record.toolResults ?? [],
      });
    }

    if (type === "agent_settled") {
      return this.record("settled", { aborted: Boolean(record.aborted) });
    }

    return null;
  }

  /** Attach to a PiClient (or any emitter that emits "event"). */
  attach(piClient) {
    const handler = (record) => this.translate(record);
    piClient.on("event", handler);
    return () => piClient.off("event", handler);
  }

  /** Events after `since`, or the full bounded snapshot when omitted. */
  snapshot(since = 0) {
    return this.events.filter((event) => event.seq > since);
  }
}
