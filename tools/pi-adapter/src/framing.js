// Strict JSONL framing for the Pi RPC protocol.
//
// Records are separated ONLY by LF ("\n"). An optional CR immediately before
// LF is stripped so CRLF input is accepted. Unicode line and paragraph
// separators (U+2028 / U+2029) are valid inside JSON strings and must never be
// treated as record boundaries -- this is why node:readline is deliberately
// not used here.

import { StringDecoder } from "node:string_decoder";

/** Serialize one protocol record into a single framed line. */
export function encodeJsonl(record) {
  return `${JSON.stringify(record)}\n`;
}

/**
 * Create an incremental decoder that calls `onRecord(parsedObject)` once per
 * complete LF-terminated line, and `onError(error, line)` for malformed JSON.
 *
 * The decoder is incremental and UTF-8 safe: a multi-byte character split
 * across two written chunks is reassembled before framing.
 */
export function createJsonlDecoder(onRecord, onError = () => {}) {
  const utf8 = new StringDecoder("utf8");
  let buffer = "";

  function consumeLine(rawLine) {
    let line = rawLine;
    if (line.endsWith("\r")) line = line.slice(0, -1);
    if (line.length === 0) return;
    let record;
    try {
      record = JSON.parse(line);
    } catch (error) {
      onError(error, line);
      return;
    }
    onRecord(record);
  }

  function drainCompleteLines() {
    let index;
    while ((index = buffer.indexOf("\n")) !== -1) {
      const line = buffer.slice(0, index);
      buffer = buffer.slice(index + 1);
      consumeLine(line);
    }
  }

  return {
    write(chunk) {
      buffer += utf8.write(chunk);
      drainCompleteLines();
    },
    end() {
      buffer += utf8.end();
      // A trailing fragment without LF is not a complete record; ignore it.
      drainCompleteLines();
      buffer = "";
    },
    get pending() {
      return buffer;
    },
  };
}
