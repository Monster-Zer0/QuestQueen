// The in-game editor bridge (client/EditorBridge.java): where it is, and how to write to it.
import { iconUrl as itemIconUrl } from "./itemSelect";

/** The bridge's port when the game serves this page itself. */
const GAME_PORT = "47821";

/**
 * Base URL of the bridge: "" when the game serves this page, else 127.0.0.1 on the game's port.
 * `?bridge=<port>` points a dev page at another bridge (the testkit mock while a game holds 47821).
 */
export function bridgeBase(loc: { port: string; search: string } = location): string {
  if (loc.port === GAME_PORT) return "";
  const asked = Number(new URLSearchParams(loc.search).get("bridge"));
  const port = Number.isInteger(asked) && asked >= 1024 && asked <= 65535 ? String(asked) : GAME_PORT;
  return `http://127.0.0.1:${port}`;
}

// QQ-2: the bridge requires the per-session nonce on every mutating request. The page holds
// the nonce itself: read it from GET /api/session and send it as X-Editor-Nonce on writes.
export async function bridgeFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const base = bridgeBase();
  const method = (init.method ?? "GET").toUpperCase();
  if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
    const session = await fetch(`${base}/api/session`);
    if (session.ok) {
      const body = (await session.json()) as { nonce?: string };
      if (body.nonce) {
        const headers = new Headers(init.headers ?? {});
        headers.set("X-Editor-Nonce", body.nonce);
        init = { ...init, headers };
      }
    }
  }
  return fetch(`${base}${path}`, init);
}

/** The game's render of an item, through the bridge. */
export function iconUrl(id: string): string {
  return itemIconUrl(bridgeBase(), id);
}
