/**
 * Aegis Auth Worker — Firebase email/password via Identity Toolkit (server-side only).
 * APK never sees FIREBASE_API_KEY or service account material.
 */

export interface Env {
  FIREBASE_API_KEY?: string;
  FIREBASE_PROJECT_ID?: string;
  FIREBASE_CLIENT_EMAIL?: string;
  FIREBASE_PRIVATE_KEY?: string;
  AEGIS_SIGNING_SECRET: string;
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
      if (path === "/auth/register" && request.method === "POST") {
        return await handleRegister(request, env);
      }
      if (path === "/policy/lockdown" && request.method === "POST") {
        return await handleLockdownCommand(request, env);
      }
      if (path === "/health") {
        return json({
          status: "ok",
          service: "aegis-worker",
          auth: "cloudflare",
          firebaseConfigured: Boolean(env.FIREBASE_API_KEY),
          project: env.FIREBASE_PROJECT_ID || null,
        });
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
      const msg = data.error?.message || "Login failed";
      const friendly =
        msg === "EMAIL_NOT_FOUND" || msg === "INVALID_EMAIL"
          ? "No account found for that email"
          : msg === "INVALID_PASSWORD" || msg === "INVALID_LOGIN_CREDENTIALS"
            ? "Incorrect email or password"
            : msg === "USER_DISABLED"
              ? "This account has been disabled"
              : msg === "TOO_MANY_ATTEMPTS_TRY_LATER"
                ? "Too many attempts — try again later"
                : msg;
      return json({ error: friendly, code: msg }, 401);
    }

    const sessionToken = await mintSessionToken(
      env,
      data.localId || body.email,
      body.email,
      Number(data.expiresIn) || 3600
    );

    return json({
      sessionToken,
      userId: data.localId,
      email: data.email || body.email,
      expiresIn: String(data.expiresIn || 3600),
      firebaseIdToken: data.idToken,
      refreshToken: data.refreshToken,
    });
  }

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

  return json(
    { error: "Firebase is not configured on the Worker. Set FIREBASE_API_KEY secret." },
    503
  );
}

async function handleRegister(request: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_API_KEY) {
    return json({ error: "Firebase not configured" }, 503);
  }
  const body = (await request.json()) as {
    email?: string;
    password?: string;
    displayName?: string;
  };
  if (!body.email || !body.password) {
    return json({ error: "email and password required" }, 400);
  }
  if (body.password.length < 6) {
    return json({ error: "password must be at least 6 characters" }, 400);
  }

  const resp = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${env.FIREBASE_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: body.email,
        password: body.password,
        displayName: body.displayName || undefined,
        returnSecureToken: true,
      }),
    }
  );
  const data = (await resp.json()) as any;
  if (!resp.ok) {
    const msg = data.error?.message || "Register failed";
    const friendly =
      msg === "EMAIL_EXISTS"
        ? "An account with this email already exists"
        : msg.includes("WEAK_PASSWORD")
          ? "Password should be at least 6 characters"
          : msg;
    return json({ error: friendly, code: msg }, 400);
  }

  const sessionToken = await mintSessionToken(
    env,
    data.localId || body.email,
    body.email,
    Number(data.expiresIn) || 3600
  );
  return json({
    sessionToken,
    userId: data.localId,
    email: data.email || body.email,
    expiresIn: String(data.expiresIn || 3600),
  });
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
    message: "Lockdown command accepted",
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
