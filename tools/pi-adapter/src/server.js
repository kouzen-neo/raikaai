// OpenAI-compatible HTTP surface for the Pi adapter.
//
//   GET  /health                 -> { ok: true }
//   GET  /events                 -> JSON snapshot of translated Pi events
//   POST /v1/abort               -> Pi RPC `abort`
//   POST /v1/agent/prompt        -> Pi RPC `prompt` + wait for agent_settled
//   POST /v1/chat/completions    -> tool shim -> RaikaAI /v1 (JSON or SSE)

import http from "node:http";
import { randomUUID } from "node:crypto";
import { PiClient } from "./pi-client.js";
import { EventTranslator } from "./event-translator.js";
import {
  injectToolSchemas,
  applyToolShimToResponse,
  buildShimmedSse,
} from "./tool-shim.js";

const MAX_BODY_BYTES = 8 * 1024 * 1024;

function sendJson(response, status, body) {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(payload),
  });
  response.end(payload);
}

function readJsonBody(request) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new Error("Request body too large"));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on("end", () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}"));
      } catch (error) {
        reject(error);
      }
    });
    request.on("error", reject);
  });
}

function upstreamUrl(baseUrl) {
  return `${String(baseUrl).replace(/\/+$/, "")}/chat/completions`;
}

// Read an upstream SSE response, collecting assistant text deltas. We cannot
// forward deltas verbatim because the tool-call JSON may be split across them.
async function collectUpstreamSse(response, onText) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let usage = null;
  let finishReason = "stop";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");
    let separator;
    while ((separator = buffer.indexOf("\n\n")) !== -1) {
      const event = buffer.slice(0, separator);
      buffer = buffer.slice(separator + 2);
      for (const rawLine of event.split("\n")) {
        const line = rawLine.replace(/\r$/, "");
        if (!line.startsWith("data:")) continue;
        const data = line.slice(5).trim();
        if (data === "[DONE]") continue;
        let json;
        try {
          json = JSON.parse(data);
        } catch {
          continue;
        }
        if (json.usage) usage = json.usage;
        const choice = json.choices && json.choices[0];
        if (choice) {
          if (choice.finish_reason) finishReason = choice.finish_reason;
          const delta = choice.delta && choice.delta.content;
          if (typeof delta === "string" && delta.length > 0) onText(delta);
        }
      }
    }
  }
  return { usage, finishReason };
}

export function createAdapter({
  upstreamBaseUrl = process.env.UPSTREAM_BASE_URL || "http://127.0.0.1:8080/v1",
  piClient = new PiClient(),
  translator = new EventTranslator(),
  logger = () => {},
} = {}) {
  translator.attach(piClient);

  async function proxyChatCompletion(request, response, body) {
    const stream = Boolean(body.stream);
    const forwarded = injectToolSchemas(body);
    const headers = { "content-type": "application/json" };
    if (request.headers.authorization) headers.authorization = request.headers.authorization;

    let upstream;
    try {
      upstream = await fetch(upstreamUrl(upstreamBaseUrl), {
        method: "POST",
        headers,
        body: JSON.stringify(forwarded),
      });
    } catch (error) {
      logger(`pi-adapter: upstream request failed: ${error.message}`);
      sendJson(response, 502, { error: { message: `Upstream unavailable: ${error.message}` } });
      return;
    }

    if (!upstream.ok) {
      const text = await upstream.text();
      logger(`pi-adapter: upstream returned ${upstream.status}`);
      response.writeHead(upstream.status, { "content-type": "application/json" });
      response.end(text);
      return;
    }

    const id = typeof body.id === "string" ? body.id : `chatcmpl-${randomUUID().replace(/-/g, "").slice(0, 12)}`;
    const model = body.model || "pi-adapter";

    if (stream) {
      let text = "";
      const { usage, finishReason } = await collectUpstreamSse(upstream, (delta) => {
        text += delta;
      });
      const payload = buildShimmedSse({ id, model, text, usage, finishReason });
      response.writeHead(200, {
        "content-type": "text/event-stream; charset=utf-8",
        "cache-control": "no-cache",
        connection: "keep-alive",
      });
      response.end(payload);
      return;
    }

    let bodyJson;
    try {
      bodyJson = await upstream.json();
    } catch (error) {
      sendJson(response, 502, { error: { message: `Invalid upstream response: ${error.message}` } });
      return;
    }
    const shimmed = applyToolShimToResponse(bodyJson);
    if (id && shimmed && typeof shimmed === "object") shimmed.id = shimmed.id || id;
    sendJson(response, 200, shimmed);
  }

  const server = http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url || "/", "http://localhost");
      const path = url.pathname;

      if (request.method === "GET" && path === "/health") {
        sendJson(response, 200, { ok: true });
        return;
      }

      if (request.method === "GET" && path === "/events") {
        sendJson(response, 200, translator.snapshot());
        return;
      }

      if (request.method === "POST" && path === "/v1/abort") {
        await piClient.abort();
        sendJson(response, 200, { ok: true, aborted: true });
        return;
      }

      if (request.method === "POST" && path === "/v1/agent/prompt") {
        const body = await readJsonBody(request);
        if (typeof body.message !== "string") {
          sendJson(response, 400, { error: { message: "message must be a string" } });
          return;
        }
        const result = await piClient.promptAndWait(body.message);
        sendJson(response, 200, { ok: true, settled: result.settled });
        return;
      }

      if (request.method === "POST" && path === "/v1/chat/completions") {
        const body = await readJsonBody(request);
        if (!Array.isArray(body.messages)) {
          sendJson(response, 400, { error: { message: "messages must be an array" } });
          return;
        }
        await proxyChatCompletion(request, response, body);
        return;
      }

      sendJson(response, 404, { error: { message: "Not found" } });
    } catch (error) {
      logger(`pi-adapter: request failed: ${error.stack || error.message}`);
      if (!response.headersSent) {
        sendJson(response, 500, { error: { message: error.message } });
      } else {
        response.end();
      }
    }
  });

  return {
    server,
    piClient,
    translator,
    close() {
      piClient.close();
      piClient.kill();
      server.close();
    },
  };
}
