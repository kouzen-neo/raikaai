// Fake RaikaAI /v1 server: accepts OpenAI-shaped requests (without tools)
// and returns assistant text containing a tool call object.

import http from "node:http";

const TOOL_OBJECT = JSON.stringify({
  tool_calls: [
    { name: "get_weather", arguments: { city: "Paris", unit: "celsius" } },
  ],
});

export function startFakeUpstream({ toolText = `Sure!\n${TOOL_OBJECT}` } = {}) {
  const requests = [];
  const authorizations = [];
  const server = http.createServer((request, response) => {
    authorizations.push(request.headers.authorization || null);
    let body = "";
    request.on("data", (chunk) => { body += chunk; });
    request.on("end", () => {
      let json;
      try {
        json = JSON.parse(body || "{}");
      } catch {
        response.writeHead(400);
        response.end("{}");
        return;
      }
      requests.push(json);

      if (json.stream) {
        response.writeHead(200, { "content-type": "text/event-stream" });
        // Split the tool JSON across deltas on purpose: the shim must buffer
        // before it can strip the JSON and emit tool_call fragments.
        const pieces = [];
        const size = Math.ceil(toolText.length / 4);
        for (let i = 0; i < toolText.length; i += size) pieces.push(toolText.slice(i, i + size));
        for (const piece of pieces) {
          response.write(`data: ${JSON.stringify({
            choices: [{ index: 0, delta: { content: piece }, finish_reason: null }],
          })}\n\n`);
        }
        response.write(`data: ${JSON.stringify({
          choices: [{ index: 0, delta: {}, finish_reason: "stop" }],
          usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
        })}\n\n`);
        response.end("data: [DONE]\n\n");
        return;
      }

      const payload = {
        id: "chatcmpl-upstream-1",
        object: "chat.completion",
        created: 1,
        model: json.model || "fake-model",
        choices: [{
          index: 0,
          message: { role: "assistant", content: toolText },
          finish_reason: "stop",
        }],
        usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
      };
      response.writeHead(200, { "content-type": "application/json" });
      response.end(JSON.stringify(payload));
    });
  });

  return new Promise((resolve) => {
    server.listen(0, "127.0.0.1", () => {
      resolve({
        server,
        requests,
        authorizations,
        url: `http://127.0.0.1:${server.address().port}/v1`,
        close: () => new Promise((done) => server.close(done)),
      });
    });
  });
}
