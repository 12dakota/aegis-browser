/**
 * Aegis Auth & Policy Cloudflare Worker
 * All secrets stay here. APK only receives short-lived session tokens.
 */

export interface Env {
  FIREBASE_API_KEY?: string;
  AEGIS_SIGNING_SECRET: string;
  /** Optional: allow demo login when Firebase is not configured */
  DEMO_EMAIL?: string;
  DEMO_PASSWORD?: string;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders() });
    }

    try {
      if (path === "/auth/login" && request.method === "POST") {
        return await handleLogin(request, env);
      }
      if (path === "/auth/validate" && request.method === "POST") {
        return await handleValidate(request, env);
      }
      if (path === "/policy/lockdown" && request.method === "POST") {
        return await handleLockdownCommand(request, env);
      }
      if (path === "/health") {
        return json({ status: "ok", service: "aegis-worker", auth: "cloudflare" });
      }
      return json({ error: "Not found" }, 404);
    } catch (e: any) {
      return json({ error: e.message || "Internal error" }, 500);
    }
  },
};

async function handleLogin(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { email?: string; password?: string };
  if (!body.email || !body.password) {
    return json({ error: "email and password required" }, 400);
  }

  // Path A: Firebase Identity Toolkit (secrets only on Worker)
  if (env.FIREBASE_API_KEY) {
    const resp = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${env.FIREBASE_API_KEY}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: body.email,
          password: body.password,
          returnSecureToken: true,
        }),
      }
    );
    const data = (await resp.json()) as any;
    if (!resp.ok) {
      return json({ error: data.error?.message || "Login failed" }, 401);
    }
    const sessionToken = await mintSessionToken(
      env,
      data.localId || body.email,
      body.email,
      3600
    );
    return json({
      sessionToken,
      userId: data.localId,
      email: body.email,
      expiresIn: "3600",
      // legacy fields for older clients
      idToken: data.idToken,
      localId: data.localId,
    });
  }

  // Path B: Demo credentials (dev only)
  if (
    env.DEMO_EMAIL &&
    env.DEMO_PASSWORD &&
    body.email === env.DEMO_EMAIL &&
    body.password === env.DEMO_PASSWORD
  ) {
    const sessionToken = await mintSessionToken(env, "demo-user", body.email, 3600);
    return json({
      sessionToken,
      userId: "demo-user",
      email: body.email,
      expiresIn: "3600",
    });
  }

  // Path C: Accept any non-empty credentials in development if no secrets configured
  // REMOVE this path for production — set FIREBASE_API_KEY or DEMO_* secrets.
  if (!env.FIREBASE_API_KEY && !env.DEMO_EMAIL) {
    const sessionToken = await mintSessionToken(
      env,
      body.email,
      body.email,
      3600
    );
    return json({
      sessionToken,
      userId: body.email,
      email: body.email,
      expiresIn: "3600",
      warning: "dev-mode-open-login",
    });
  }

  return json({ error: "Invalid credentials" }, 401);
}

async function handleValidate(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { sessionToken?: string };
  if (!body.sessionToken) return json({ error: "sessionToken required" }, 400);
  const ok = await verifySessionToken(env, body.sessionToken);
  return json({ valid: ok });
}

async function handleLockdownCommand(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as {
    deviceId?: string;
    enable?: boolean;
    reason?: string;
    enrollmentToken?: string;
  };
  return json({
    status: "queued",
    message: "Lockdown command accepted (deliver via your push channel)",
    payload: {
      command: "lockdown",
      enable: String(body.enable ?? true),
      reason: body.reason || "Parent / MDM remote command",
      token: body.enrollmentToken || "",
    },
  });
}

async function mintSessionToken(
  env: Env,
  userId: string,
  email: string,
  expiresIn: number
): Promise<string> {
  const secret = env.AEGIS_SIGNING_SECRET || "aegis-dev-signing-secret-change-me";
  const exp = Math.floor(Date.now() / 1000) + expiresIn;
  const payload = btoa(JSON.stringify({ sub: userId, email, exp }));
  const sig = await hmacSign(secret, payload);
  return `aegis.${payload}.${sig}`;
}

async function verifySessionToken(env: Env, token: string): Promise<boolean> {
  try {
    const parts = token.split(".");
    if (parts.length !== 3 || parts[0] !== "aegis") return false;
    const secret = env.AEGIS_SIGNING_SECRET || "aegis-dev-signing-secret-change-me";
    const expected = await hmacSign(secret, parts[1]);
    if (expected !== parts[2]) return false;
    const payload = JSON.parse(atob(parts[1]));
    if (payload.exp && payload.exp < Math.floor(Date.now() / 1000)) return false;
    return true;
  } catch {
    return false;
  }
}

async function hmacSign(secret: string, data: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(data));
  return btoa(String.fromCharCode(...new Uint8Array(sig)));
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders() },
  });
}

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
  };
}
