// Entry point: start the adapter HTTP service and its Pi RPC child.

import { createAdapter } from "./server.js";

const host = process.env.HOST || "127.0.0.1";
const port = Number(process.env.PORT || 8787);
const adapter = createAdapter({ logger: (message) => process.stderr.write(`${message}\n`) });

adapter.server.listen(port, host, () => {
  process.stderr.write(`pi-adapter listening on http://${host}:${port}\n`);
});

function shutdown() {
  adapter.close();
}
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
