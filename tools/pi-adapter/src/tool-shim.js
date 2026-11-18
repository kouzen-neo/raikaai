// Tool-calling shim for RaikaAI's Ktor /v1, which has no `tools` field and no
// `tool_calls` in its responses. The adapter injects the OpenAI tools array
// into the system prompt, then parses a `{"tool_calls":[...]}` object out of
// the assistant text and rebuilds a proper OpenAI-compatible response.

import { randomUUID } from "node:crypto";

const TOOL_INSTRUCTIONS = [
  "TOOL CALLING PROTOCOL",
  "You have access to the tools listed below as a JSON array.",
  "When the user's request requires one or more tools, respond with ONLY a JSON object in exactly this shape and no other prose:",
  '{"tool_calls":[{"name":"<tool_name>","arguments":{...}}, ...]}',
  'Each "name" MUST be one of the tool names below. "arguments" MUST be a JSON object that satisfies that tool\'s parameter schema.',
  "A fenced ```json block containing that object is also accepted. Do not invent tool names.",
  "When no tool is needed, answer normally as plain text without any JSON block.",
].join("\n");

export function buildToolPrompt(tools) {
  return `${TOOL_INSTRUCTIONS}\n\nAVAILABLE TOOLS (JSON Schema):\n${JSON.stringify(tools, null, 2)}`;
}

/** Clone a chat request, moving tool schemas into the system prompt. */
export function injectToolSchemas(request) {
  const tools = Array.isArray(request.tools) && request.tools.length > 0
    ? request.tools
    : null;
  const { tools: _tools, tool_choice: _toolChoice, ...rest } = request;
  if (!tools) return rest;

  const messages = Array.isArray(request.messages)
    ? request.messages.map((message) => ({ ...message }))
    : [];
  const block = buildToolPrompt(tools);
  const systemIndex = messages.findIndex((message) => message && message.role === "system");

  if (systemIndex >= 0) {
    const current = messages[systemIndex].content;
    if (typeof current === "string") {
      messages[systemIndex].content = `${current}\n\n${block}`;
    } else if (Array.isArray(current)) {
      messages[systemIndex].content = [...current, { type: "text", text: block }];
    } else {
      messages[systemIndex].content = block;
    }
  } else {
    messages.unshift({ role: "system", content: block });
  }

  return { ...rest, messages };
}

function normalizeToolCall(entry) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) return null;
  const fn = entry.function && typeof entry.function === "object" ? entry.function : entry;
  const name = typeof fn.name === "string" ? fn.name : typeof entry.name === "string" ? entry.name : null;
  if (!name) return null;

  let args = fn.arguments !== undefined
    ? fn.arguments
    : entry.arguments !== undefined
      ? entry.arguments
      : entry.parameters ?? entry.input ?? {};
  if (typeof args === "string") {
    try {
      args = JSON.parse(args);
    } catch {
      args = {};
    }
  }
  if (!args || typeof args !== "object" || Array.isArray(args)) args = {};
  return { name, arguments: args };
}

// Find every balanced {...} span in the text while respecting JSON strings and
// escapes. This lets a tool object be embedded in surrounding prose.
function balancedObjectSpans(text) {
  const spans = [];
  for (let start = 0; start < text.length; start += 1) {
    if (text[start] !== "{") continue;
    let depth = 0;
    let inString = false;
    let escaped = false;
    for (let i = start; i < text.length; i += 1) {
      const char = text[i];
      if (inString) {
        if (escaped) escaped = false;
        else if (char === "\\") escaped = true;
        else if (char === '"') inString = false;
        continue;
      }
      if (char === '"') inString = true;
      else if (char === "{") depth += 1;
      else if (char === "}") {
        depth -= 1;
        if (depth === 0) {
          spans.push({ raw: text.slice(start, i + 1), start, end: i + 1 });
          start = i;
          break;
        }
      }
    }
  }
  return spans;
}

/**
 * Parse a `{"tool_calls":[...]}` object out of assistant text.
 * Accepts a bare object, a fenced ```json block, or an object embedded in
 * surrounding prose. Returns null when no valid tool call is present.
 */
export function parseToolCalls(text) {
  if (typeof text !== "string" || text.trim().length === 0) return null;

  const candidates = [];
  const trimmed = text.trim();
  const trimmedStart = text.indexOf(trimmed);
  candidates.push({ raw: trimmed, start: trimmedStart, end: trimmedStart + trimmed.length });

  const fencePattern = /```(?:json)?\s*([\s\S]*?)```/gi;
  let match;
  while ((match = fencePattern.exec(text)) !== null) {
    const raw = match[1].trim();
    if (!raw) continue;
    // Consume one adjacent line break so stripping the fenced block does not
    // leave a doubled blank line in the visible text.
    let end = match.index + match[0].length;
    if (text[end] === "\r") end += 1;
    if (text[end] === "\n") end += 1;
    candidates.push({ raw, start: match.index, end });
  }
  candidates.push(...balancedObjectSpans(text));

  const seen = new Set();
  for (const candidate of candidates) {
    const key = `${candidate.start}:${candidate.end}:${candidate.raw}`;
    if (seen.has(key)) continue;
    seen.add(key);

    let parsed;
    try {
      parsed = JSON.parse(candidate.raw);
    } catch {
      continue;
    }
    if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.tool_calls)) continue;
    const calls = parsed.tool_calls.map(normalizeToolCall).filter(Boolean);
    if (calls.length === 0) continue;

    return {
      toolCalls: calls,
      visibleText: (text.slice(0, candidate.start) + text.slice(candidate.end)).trim(),
      matchedRaw: candidate.raw,
    };
  }
  return null;
}

function newToolCallId() {
  return `call_${randomUUID().replace(/-/g, "").slice(0, 12)}`;
}

export function toOpenAiToolCalls(calls) {
  return calls.map((call) => ({
    id: newToolCallId(),
    type: "function",
    function: {
      name: call.name,
      arguments: JSON.stringify(call.arguments),
    },
  }));
}

function contentToString(content) {
  if (typeof content === "string") return content;
  if (Array.isArray(content)) {
    return content
      .filter((part) => part && typeof part.text === "string")
      .map((part) => part.text)
      .join("");
  }
  return "";
}

/** Rewrite a non-streaming upstream completion with tool_calls. */
export function applyToolShimToResponse(body) {
  if (!body || !Array.isArray(body.choices) || body.choices.length === 0) return body;
  const choice = body.choices[0];
  const text = contentToString(choice.message && choice.message.content);
  const parsed = parseToolCalls(text);
  if (!parsed) return body;

  const message = {
    ...(choice.message || {}),
    content: parsed.visibleText,
    tool_calls: toOpenAiToolCalls(parsed.toolCalls),
  };
  return {
    ...body,
    choices: [{ ...choice, message, finish_reason: "tool_calls" }],
  };
}

/**
 * Build a complete OpenAI SSE stream from the buffered upstream assistant
 * text. Buffering is required because a tool-call JSON object can span many
 * provider deltas and must be removed before any visible text is emitted.
 */
export function buildShimmedSse({ id, model, text, usage, finishReason }) {
  const parsed = parseToolCalls(text);
  const created = Math.floor(Date.now() / 1000);
  const chunk = (delta, finish = null) => ({
    id,
    object: "chat.completion.chunk",
    created,
    model,
    choices: [{ index: 0, delta, finish_reason: finish }],
  });

  const chunks = [];
  if (parsed) {
    const toolCalls = toOpenAiToolCalls(parsed.toolCalls);
    chunks.push(chunk({ role: "assistant", content: parsed.visibleText }));
    toolCalls.forEach((toolCall, index) => {
      chunks.push(chunk({
        tool_calls: [{
          index,
          id: toolCall.id,
          type: "function",
          function: { name: toolCall.function.name, arguments: "" },
        }],
      }));
      chunks.push(chunk({
        tool_calls: [{
          index,
          function: { arguments: toolCall.function.arguments },
        }],
      }));
    });
    chunks.push(chunk({}, "tool_calls"));
  } else {
    chunks.push(chunk({ role: "assistant", content: text }));
    chunks.push(chunk({}, finishReason || "stop"));
  }
  if (usage) chunks.push({ ...chunk({}), usage });

  return `${chunks.map((part) => `data: ${JSON.stringify(part)}\n\n`).join("")}data: [DONE]\n\n`;
}
