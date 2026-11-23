import { DurableObject } from "cloudflare:workers";

const ACTIONS = new Set([
  "VOLUME_UP", "VOLUME_DOWN", "MUTE", "HOME", "BACK", "POWER",
  "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "OK"
]);
const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { "content-type": "application/json", "access-control-allow-origin": "*" }
});
const randomToken = (bytes = 32) => {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return btoa(String.fromCharCode(...data)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
};
const sha256 = async (value) => {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
};
const bearer = (request) => {
  const value = request.headers.get("Authorization") || "";
  return value.startsWith("Bearer ") ? value.slice(7).trim() : "";
};

export class DeviceHub extends DurableObject {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/create" && request.method === "POST") {
      const body = await request.json();
      const existing = await this.ctx.storage.get("pair");
      if (existing && existing.expiresAt > Date.now() && !existing.consumed) return json({ error: "Code collision" }, 409);
      await this.ctx.storage.put("pair", { deviceId: body.deviceId, expiresAt: Date.now() + 300000, consumed: false });
      return json({ ok: true });
    }
    if (url.pathname === "/consume" && request.method === "POST") {
      const result = await this.ctx.storage.transaction(async txn => {
        const pair = await txn.get("pair");
        if (!pair || pair.consumed || pair.expiresAt < Date.now()) return null;
        pair.consumed = true;
        await txn.put("pair", pair);
        return pair.deviceId;
      });
      return result ? json({ deviceId: result }) : json({ error: "Pairing code is invalid or expired." }, 404);
    }
    if (url.pathname === "/authorize-tv" && request.method === "POST") {
      const body = await request.json();
      const state = await this.ctx.storage.get("state");
      if (!state?.deviceId || await sha256(body.receiverToken || "") !== state.receiverHash) return json({ error: "Unauthorized TV" }, 401);
      return json({ ok: true });
    }
    if (url.pathname === "/init" && request.method === "POST") {
      const body = await request.json();
      await this.ctx.storage.put("state", {
        deviceId: body.deviceId,
        receiverHash: await sha256(body.receiverToken),
        remoteHash: null,
        remoteHashes: [],
        paired: false
      });
      return json({ ok: true });
    }
    if (url.pathname === "/claim" && request.method === "POST") {
      const body = await request.json();
      const state = await this.ctx.storage.get("state");
      if (!state?.deviceId) return json({ error: "Pairing code is invalid or expired." }, 404);
      const tokenHash = await sha256(body.remoteToken);
      state.remoteHashes = Array.isArray(state.remoteHashes) ? state.remoteHashes : (state.remoteHash ? [state.remoteHash] : []);
      if (!state.remoteHashes.includes(tokenHash)) state.remoteHashes.push(tokenHash);
      state.remoteHash = state.remoteHashes[0] || tokenHash;
      state.paired = true;
      await this.ctx.storage.put("state", state);
      return json({ device_id: state.deviceId, paired: true });
    }
    if (url.pathname === "/status") {
      const state = await this.ctx.storage.get("state");
      const suppliedHash = await sha256(bearer(request));
      const allowedHashes = state?.remoteHashes || (state?.remoteHash ? [state.remoteHash] : []);
      if (!state?.paired || !allowedHashes.includes(suppliedHash)) return json({ error: "Unauthorized" }, 401);
      return json({ paired: true, connected: this.ctx.getWebSockets("tv").some(ws => ws.readyState === WebSocket.OPEN) });
    }
    if (url.pathname === "/ws") {
      if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") return json({ error: "WebSocket upgrade required" }, 426);
      const role = url.searchParams.get("role");
      const state = await this.ctx.storage.get("state");
      const token = bearer(request);
      if (!state?.deviceId || !token) return json({ error: "Unauthorized" }, 401);
      const hash = await sha256(token);
      if (role === "tv" && hash !== state.receiverHash) return json({ error: "Unauthorized TV" }, 401);
      const allowedHashes = state.remoteHashes || (state.remoteHash ? [state.remoteHash] : []);
      if (role === "remote" && (!state.paired || !allowedHashes.includes(hash))) return json({ error: "Unauthorized remote" }, 401);
      if (role !== "tv" && role !== "remote") return json({ error: "Invalid socket role" }, 400);
      const pair = new WebSocketPair();
      const [client, server] = Object.values(pair);
      this.ctx.acceptWebSocket(server, [role]);
      server.serializeAttachment({ role });
      return new Response(null, { status: 101, webSocket: client });
    }
    if (url.pathname === "/command" && request.method === "POST") {
      const state = await this.ctx.storage.get("state");
      const suppliedHash = await sha256(bearer(request));
      const allowedHashes = state?.remoteHashes || (state?.remoteHash ? [state.remoteHash] : []);
      if (!state?.paired || !allowedHashes.includes(suppliedHash)) return json({ error: "Unauthorized" }, 401);
      const body = await request.json();
      if (!ACTIONS.has(body.action)) return json({ error: "Unsupported action" }, 400);
      const tv = this.ctx.getWebSockets("tv").find(ws => ws.readyState === WebSocket.OPEN);
      if (!tv) return json({ error: "TV offline" }, 503);
      const id = crypto.randomUUID();
      tv.send(JSON.stringify({ type: "command", id, action: body.action }));
      return json({ sent: true, id, action: body.action });
    }
    return json({ error: "Not found" }, 404);
  }

  async webSocketMessage(ws, message) {
    let data;
    try { data = JSON.parse(typeof message === "string" ? message : new TextDecoder().decode(message)); }
    catch { ws.send(JSON.stringify({ type: "error", error: "Invalid message" })); return; }
    const role = ws.deserializeAttachment()?.role;
    if (data.type === "ping") { ws.send(JSON.stringify({ type: "pong" })); return; }
    if (role === "remote" && data.type === "get_state") {
      const tv = this.ctx.getWebSockets("tv").find(client => client.readyState === WebSocket.OPEN);
      if (tv) tv.send(JSON.stringify({ type: "get_state" }));
      return;
    }
    if (role === "remote") {
      if (!ACTIONS.has(data.action)) { ws.send(JSON.stringify({ type: "error", error: "Unsupported action" })); return; }
      const tv = this.ctx.getWebSockets("tv").find(client => client.readyState === WebSocket.OPEN);
      if (!tv) { ws.send(JSON.stringify({ type: "error", error: "TV offline" })); return; }
      const id = crypto.randomUUID();
      tv.send(JSON.stringify({ type: "command", id, action: data.action }));
      ws.send(JSON.stringify({ type: "sent", id, action: data.action }));
      return;
    }
    if (role === "tv" && (data.type === "ack" || data.type === "tv_state")) {
      for (const remote of this.ctx.getWebSockets("remote")) {
        if (remote.readyState === WebSocket.OPEN) remote.send(JSON.stringify(data));
      }
    }
  }

  async webSocketClose(ws, code, reason) {
    try { ws.close(code, reason); } catch {}
  }
  async webSocketError(ws) {
    try { ws.close(1011, "Socket error"); } catch {}
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: {
      "access-control-allow-origin": "*", "access-control-allow-methods": "GET,POST,OPTIONS",
      "access-control-allow-headers": "Authorization,Content-Type"
    }});
    if (url.pathname === "/health" || url.pathname === "/") return json({ name: "Cassim TV Relay", status: "online", edge: true });
    if (url.pathname === "/tv/pair/start" && request.method === "POST") {
      const deviceId = crypto.randomUUID();
      const receiverToken = randomToken(32);
      const device = env.DEVICES.getByName("device:" + deviceId);
      await device.fetch("https://device/init", { method: "POST", body: JSON.stringify({ deviceId, receiverToken }) });
      for (let attempt = 0; attempt < 8; attempt++) {
        const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, "0");
        const codeStub = env.DEVICES.getByName("code:" + code);
        const created = await codeStub.fetch("https://code/create", { method: "POST", body: JSON.stringify({ deviceId }) });
        if (created.status === 200) return json({ device_id: deviceId, pairing_code: code, receiver_token: receiverToken, expires_in_seconds: 300 });
      }
      return json({ error: "Could not allocate a pairing code. Try again." }, 503);
    }
    if (url.pathname === "/tv/pair/add" && request.method === "POST") {
      const body = await request.json();
      if (!body.device_id || !body.receiver_token) return json({ error: "TV credentials required." }, 400);
      const device = env.DEVICES.getByName("device:" + body.device_id);
      const auth = await device.fetch("https://device/authorize-tv", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ receiverToken: body.receiver_token })
      });
      if (!auth.ok) return json({ error: "TV authorization failed." }, 401);
      for (let attempt = 0; attempt < 8; attempt++) {
        const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, "0");
        const codeStub = env.DEVICES.getByName("code:" + code);
        const created = await codeStub.fetch("https://code/create", { method: "POST", body: JSON.stringify({ deviceId: body.device_id }) });
        if (created.status === 200) return json({ pairing_code: code, expires_in_seconds: 300 });
      }
      return json({ error: "Could not allocate a pairing code. Try again." }, 503);
    }
    if (url.pathname === "/tv/pair/claim" && request.method === "POST") {
      const body = await request.json();
      if (!/^\d{6}$/.test(body.code || "")) return json({ error: "Enter a six-digit code." }, 400);
      const codeStub = env.DEVICES.getByName("code:" + body.code);
      const consumed = await codeStub.fetch("https://code/consume", { method: "POST" });
      if (!consumed.ok) return consumed;
      const { deviceId } = await consumed.json();
      const remoteToken = randomToken(40);
      const device = env.DEVICES.getByName("device:" + deviceId);
      const claimed = await device.fetch("https://device/claim", { method: "POST", body: JSON.stringify({ remoteToken }) });
      if (!claimed.ok) return claimed;
      return json({ device_id: deviceId, remote_token: remoteToken, paired: true });
    }
    const match = url.pathname.match(/^\/tv\/(receiver|remote|status)\/([a-f0-9-]+)$/i);
    if (match) {
      const [, kind, deviceId] = match;
      const stub = env.DEVICES.getByName("device:" + deviceId);
      if (kind === "status") return stub.fetch("https://device/status", request);
      if (kind === "receiver" || kind === "remote") {
        const target = new URL("https://device/ws");
        target.searchParams.set("role", kind === "receiver" ? "tv" : "remote");
        return stub.fetch(new Request(target, request));
      }
    }
    if (url.pathname === "/tv/command" && request.method === "POST") {
      const body = await request.json();
      const stub = env.DEVICES.getByName("device:" + body.device_id);
      const forwarded = new Request("https://device/command", {
        method: "POST",
        headers: { "Authorization": request.headers.get("Authorization") || "", "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      return stub.fetch(forwarded);
    }
    return json({ error: "Not found" }, 404);
  }
};
