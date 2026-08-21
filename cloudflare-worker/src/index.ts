/**
 * Aegis Auth & Policy Cloudflare Worker
 *
 * PURPOSE
 * -------
 * Hold all sensitive keys and secrets in the cloud (never in the APK).
 * The Android browser only ever receives short-lived tokens.
 *
 * Flow:
 * 1. App calls POST /auth/login  { email, password }  (or OAuth code)
 * 2. Worker verifies against Firebase Auth REST / Admin SDK
 * 3. Worker issues a short-lived Firebase custom token (or JWT)
 * 4. App does firebase.auth().signInWithCustomToken(token)
 *
 * Additional endpoints:
 * - POST /policy/push          (parent / MDM pushes new policy or lockdown)
 * - POST /extension/approve    (parent approves an extension for a child)
 * - POST /fcm/send             (send lockdown / install_extension command to device)
 *
 * Environment secrets (set in Cloudflare dashboard / wrangler secret):
 * - FIREBASE_API_KEY
 * - FIREBASE_PROJECT_ID
 * - FIREBASE_CLIENT_EMAIL
 * - FIREBASE_PRIVATE_KEY
 * - FCM_SERVER_KEY (or use HTTP v1)
 * - AEGIS_SIGNING_SECRET
 */

export interface Env {
  FIREBASE_API_KEY: string;
  FIREBASE_PROJECT_ID: string;
  FIREBASE_CLIENT_EMAIL: string;
  FIREBASE_PRIVATE_KEY: string;
  AEGIS_SIGNING_SECRET: string;
  // Add FCM credentials as needed
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    // CORS for the Android app / parent console
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: corsHeaders(),
      });
    }

    try {
      if (path === "/auth/login" && request.method === "POST") {
        return await handleLogin(request, env);
      }
      if (path === "/auth/custom-token" && request.method === "POST") {
        return await handleCustomToken(request, env);
      }
      if (path === "/policy/lockdown" && request.method === "POST") {
        return await handleLockdownCommand(request, env);
      }
      if (path === "/health") {
        return json({ status: "ok", service: "aegis-worker" });
      }
      return json({ error: "Not found" }, 404);
    } catch (e: any) {
      return json({ error: e.message || "Internal error" }, 500);
    }
  },
};

async function handleLogin(request: Request, env: Env): Promise<Response> {
  const body = await request.json() as { email?: string; password?: string };
  if (!body.email || !body.password) {
    return json({ error: "email and password required" }, 400);
  }

  // Call Firebase Auth REST API (signInWithPassword)
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

  const data = await resp.json() as any;
  if (!resp.ok) {
    return json({ error: data.error?.message || "Login failed" }, 401);
  }

  // Optionally mint a shorter-lived custom token here using Firebase Admin
  // For now return the idToken + refreshToken (client should still prefer custom token flow)
  return json({
    idToken: data.idToken,
    refreshToken: data.refreshToken,
    localId: data.localId,
    expiresIn: data.expiresIn,
  });
}

async function handleCustomToken(request: Request, env: Env): Promise<Response> {
  // Production: verify a session or parent-approved code, then use Firebase Admin
  // to createCustomToken(uid). Requires the service account private key in env.
  // This is the recommended path so the APK never sees long-lived credentials.
  return json({
    message: "Implement createCustomToken with Firebase Admin SDK (service account)",
    note: "Store FIREBASE_PRIVATE_KEY as a Cloudflare secret. Never ship it in the APK.",
  });
}

async function handleLockdownCommand(request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    deviceFcmToken?: string;
    enable?: boolean;
    reason?: string;
    enrollmentToken?: string;
  };

  if (!body.deviceFcmToken) {
    return json({ error: "deviceFcmToken required" }, 400);
  }

  // In production: send FCM data message with command=lockdown
  // using the FCM HTTP v1 API and a short-lived OAuth token from the service account.
  // The Android app validates the enrollmentToken / JWT before applying.

  return json({
    status: "queued",
    message: "Lockdown command would be sent via FCM in production",
    payload: {
      command: "lockdown",
      enable: String(body.enable ?? true),
      reason: body.reason || "Parent / MDM remote command",
      token: body.enrollmentToken || "",
    },
  });
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...corsHeaders(),
    },
  });
}

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
  };
}
