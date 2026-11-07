import { createServer } from "node:http";
import { health, lookup, report } from "./handlers.js";

const routes: Record<string, (request: Request) => Promise<Response>> = {
  "GET /api/health": health,
  "POST /api/lookup": lookup,
  "POST /api/report": report,
};

const port = Number(process.env.PORT ?? 3000);

createServer(async (req, res) => {
  const route = routes[`${req.method} ${req.url}`];
  if (!route) {
    res.writeHead(404).end();
    return;
  }
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  const body = Buffer.concat(chunks).toString();
  try {
    const response = await route(
      new Request(`http://localhost${req.url}`, {
        method: req.method,
        body: req.method === "POST" ? body : undefined,
      }),
    );
    const text = await response.text();
    console.log(`${new Date().toISOString()} ${req.method} ${req.url} ${response.status} <- ${body} -> ${text}`);
    res.writeHead(response.status, { "content-type": "application/json" });
    res.end(text);
  } catch (error) {
    console.error(error);
    res.writeHead(500).end();
  }
}).listen(port, () => console.log(`http://localhost:${port}`));
