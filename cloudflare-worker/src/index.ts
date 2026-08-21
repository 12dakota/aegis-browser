/**
 * Aegis Auth + MDM Policy Worker
 * Secrets stay here. Firestore (project ominium) holds allowlists + browser policy.
 *
 * Collections (Firestore):
 *   aegis/config/policy          - global browser policy (domains, lockdown, times)
 *   aegis/config/extensions      - extension allowlist
 *   aegis/orgs/{orgId}/policy    - optional per-org override
 */

export interface Env {
  FIREBASE_API_KEY?: string;
  FIREBASE_PROJECT_ID?: string;
  FIREBASE_CLIENT_EMAIL?: string;
  FIREBASE_PRIVATE_KEY?: string;
  AEGIS_SIGNING_SECRET: string;
  DEMO_EMAIL?: string;
  DEMO_PASSWORD?: string;
  ADMIN_PASSWORD?: string;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders() });
    }

    try {
      // --- Auth ---
      if (path === "/auth/login" && request.method === "POST") {
        return await handleLogin(request, env);
      }
      if (path === "/auth/validate" && request.method === "POST") {
        return await handleValidate(request, env);
      }
      if (path === "/auth/register" && request.method === "POST") {
        return await handleRegister(request, env);
      }

      // --- Policy (device / parent) ---
      if (path === "/policy/get" && request.method === "GET") {
        return await handlePolicyGet(request, env);
      }
      if (path === "/policy/set" && request.method === "POST") {
        return await handlePolicySet(request, env);
      }
      if (path === "/policy/lockdown" && request.method === "POST") {
        return await handleLockdownCommand(request, env);
      }

      // --- Extension allowlist ---
      if (path === "/extensions/allowlist" && request.method === "GET") {
        return await handleExtensionsGet(env);
      }
      if (path === "/extensions/allowlist" && request.method === "POST") {
        return await handleExtensionsSet(request, env);
      }

      // --- MDM Admin UI ---
      if (path === "/admin" || path === "/admin/") {
        return new Response(adminHtml(), {
          headers: { "Content-Type": "text/html; charset=utf-8", ...corsHeaders() },
        });
      }
      if (path === "/admin/api/bootstrap" && request.method === "POST") {
        return await handleBootstrap(request, env);
      }

      if (path === "/health") {
        return json({
          status: "ok",
          service: "aegis-worker",
          auth: "cloudflare",
          firebaseConfigured: Boolean(env.FIREBASE_API_KEY),
          project: env.FIREBASE_PROJECT_ID || null,
          mdm: true,
        });
      }

      return json({ error: "Not found" }, 404);
    } catch (e: any) {
      return json({ error: e.message || "Internal error" }, 500);
    }
  },
};

// ---------- Auth (unchanged core) ----------

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

  return json({ error: "Firebase is not configured on the Worker." }, 503);
}

async function handleRegister(request: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_API_KEY) return json({ error: "Firebase not configured" }, 503);
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
    return json({ error: data.error?.message || "Register failed" }, 400);
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
  return json({ valid: await verifySessionToken(env, body.sessionToken) });
}

async function handleLockdownCommand(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as {
    enable?: boolean;
    reason?: string;
    adminPassword?: string;
  };
  if (!(await checkAdmin(request, env, body.adminPassword))) {
    return json({ error: "Unauthorized" }, 401);
  }
  const policy = await getPolicyDoc(env);
  policy.lockdownEnabled = body.enable ?? true;
  policy.lockdownReason = body.reason || "Parent / MDM remote command";
  await putPolicyDoc(env, policy);
  return json({ status: "ok", policy });
}

// ---------- Policy / Extensions ----------

async function handlePolicyGet(_request: Request, env: Env): Promise<Response> {
  const policy = await getPolicyDoc(env);
  const extensions = await getExtensionsDoc(env);
  return json({ policy, extensions });
}

async function handlePolicySet(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as any;
  if (!(await checkAdmin(request, env, body.adminPassword))) {
    return json({ error: "Unauthorized — set ADMIN_PASSWORD secret or use session" }, 401);
  }
  const current = await getPolicyDoc(env);
  const next = {
    ...current,
    ...body.policy,
    updatedAt: new Date().toISOString(),
  };
  await putPolicyDoc(env, next);
  return json({ status: "ok", policy: next });
}

async function handleExtensionsGet(env: Env): Promise<Response> {
  return json(await getExtensionsDoc(env));
}

async function handleExtensionsSet(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as any;
  if (!(await checkAdmin(request, env, body.adminPassword))) {
    return json({ error: "Unauthorized" }, 401);
  }
  const doc = {
    allowlist: body.allowlist || [],
    updatedAt: new Date().toISOString(),
  };
  await putExtensionsDoc(env, doc);
  return json({ status: "ok", ...doc });
}

async function handleBootstrap(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { adminPassword?: string };
  if (!(await checkAdmin(request, env, body.adminPassword))) {
    return json({ error: "Unauthorized" }, 401);
  }
  const policy = defaultPolicy();
  const extensions = defaultExtensions();
  await putPolicyDoc(env, policy);
  await putExtensionsDoc(env, extensions);
  return json({ status: "bootstrapped", policy, extensions });
}

function defaultPolicy() {
  return {
    lockdownEnabled: false,
    lockdownReason: "",
    forceSystemProxy: true,
    pacUrl: "https://pac.cloudflare-gateway.com/e2950b77bc288e9366bebbcccf1c25a8/WIyYdBdISzKaPnuqVMMyqg",
    // Empty allowedDomains = allow all (except category blocks). Non-empty = whitelist mode.
    allowedDomains: [] as string[],
    blockedDomains: [] as string[],
    blockedCategories: ["adult", "gambling"],
    timeStart: null as string | null,
    timeEnd: null as string | null,
    disableDownloads: true,
    disableUploads: true,
    forceSafeSearch: true,
    disableIncognito: true,
    updatedAt: new Date().toISOString(),
  };
}

function defaultExtensions() {
  return {
    allowlist: [
      {
        id: "ublock-origin",
        name: "uBlock Origin",
        description: "Efficient wide-spectrum content blocker",
        amoId: "uBlock0@raymondhill.net",
        amoXpiUrl:
          "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
        chromeStoreId: "cjpalhdlnbpafilfilklakntgclifmdh",
        chromeStoreUrl:
          "https://chromewebstore.google.com/detail/ublock-origin/cjpalhdlnbpafilfilklakntgclifmdh",
        enabledByDefault: true,
      },
    ],
    updatedAt: new Date().toISOString(),
  };
}

async function getPolicyDoc(env: Env): Promise<any> {
  const doc = await firestoreGet(env, "aegis/config/policy");
  return doc || defaultPolicy();
}

async function putPolicyDoc(env: Env, data: any): Promise<void> {
  await firestoreSet(env, "aegis/config/policy", data);
}

async function getExtensionsDoc(env: Env): Promise<any> {
  const doc = await firestoreGet(env, "aegis/config/extensions");
  return doc || defaultExtensions();
}

async function putExtensionsDoc(env: Env, data: any): Promise<void> {
  await firestoreSet(env, "aegis/config/extensions", data);
}

// ---------- Firestore REST via service account ----------

async function getAccessToken(env: Env): Promise<string | null> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) return null;
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(
    JSON.stringify({
      iss: env.FIREBASE_CLIENT_EMAIL,
      scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/cloud-platform",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    })
  );
  const unsigned = `${header}.${claim}`;
  const keyPem = env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n");
  const key = await importPrivateKey(keyPem);
  const sig = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    key,
    new TextEncoder().encode(unsigned)
  );
  const jwt = `${unsigned}.${b64url(sig)}`;
  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  const data = (await resp.json()) as any;
  if (!resp.ok) throw new Error("token: " + (data.error_description || data.error));
  return data.access_token as string;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const raw = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    raw,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
}

/** path like "aegis/config/policy" -> collection/doc segments for Firestore REST */
function toFirestoreName(projectId: string, path: string): string {
  // Use a single document under collection aegis: document config_policy
  // path "aegis/config/policy" => projects/.../databases/(default)/documents/aegis/config_policy
  const safe = path.replace(/\//g, "_");
  return `projects/${projectId}/databases/(default)/documents/aegis/${safe}`;
}

async function firestoreGet(env: Env, path: string): Promise<any | null> {
  const project = env.FIREBASE_PROJECT_ID || "ominium";
  const token = await getAccessToken(env);
  if (!token) return null;
  const name = toFirestoreName(project, path);
  const resp = await fetch(`https://firestore.googleapis.com/v1/${name}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (resp.status === 404) return null;
  const data = (await resp.json()) as any;
  if (!resp.ok) {
    console.log("firestore get err", data);
    return null;
  }
  return fromFirestoreFields(data.fields || {});
}

async function firestoreSet(env: Env, path: string, obj: any): Promise<void> {
  const project = env.FIREBASE_PROJECT_ID || "ominium";
  const token = await getAccessToken(env);
  if (!token) throw new Error("No service account — cannot write Firestore");
  const name = toFirestoreName(project, path);
  const body = { fields: toFirestoreFields(obj) };
  const resp = await fetch(`https://firestore.googleapis.com/v1/${name}?documentId=`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  // PATCH full name
  const resp2 = await fetch(`https://firestore.googleapis.com/v1/${name}`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  if (!resp2.ok) {
    const err = await resp2.text();
    throw new Error("Firestore write failed: " + err);
  }
}

function toFirestoreFields(obj: any): any {
  const fields: any = {};
  for (const [k, v] of Object.entries(obj)) {
    fields[k] = toFirestoreValue(v);
  }
  return fields;
}

function toFirestoreValue(v: any): any {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) {
    return { arrayValue: { values: v.map(toFirestoreValue) } };
  }
  if (typeof v === "object") {
    return { mapValue: { fields: toFirestoreFields(v) } };
  }
  return { stringValue: String(v) };
}

function fromFirestoreFields(fields: any): any {
  const out: any = {};
  for (const [k, v] of Object.entries(fields || {})) {
    out[k] = fromFirestoreValue(v);
  }
  return out;
}

function fromFirestoreValue(v: any): any {
  if (v.nullValue !== undefined) return null;
  if (v.booleanValue !== undefined) return v.booleanValue;
  if (v.integerValue !== undefined) return Number(v.integerValue);
  if (v.doubleValue !== undefined) return v.doubleValue;
  if (v.stringValue !== undefined) return v.stringValue;
  if (v.arrayValue) {
    return (v.arrayValue.values || []).map(fromFirestoreValue);
  }
  if (v.mapValue) return fromFirestoreFields(v.mapValue.fields || {});
  return null;
}

async function checkAdmin(request: Request, env: Env, bodyPassword?: string): Promise<boolean> {
  // Prefer ADMIN_PASSWORD secret; also accept valid session token as parent
  const admin = env.ADMIN_PASSWORD;
  if (admin && bodyPassword && bodyPassword === admin) return true;
  const auth = request.headers.get("Authorization") || "";
  if (auth.startsWith("Bearer ")) {
    const tok = auth.slice(7);
    if (await verifySessionToken(env, tok)) return true;
  }
  // If no ADMIN_PASSWORD set, allow bootstrap with any session (dev)
  if (!admin) return true;
  return false;
}

// ---------- Tokens ----------

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

function b64url(data: ArrayBuffer | string): string {
  let str: string;
  if (typeof data === "string") {
    str = btoa(data);
  } else {
    str = btoa(String.fromCharCode(...new Uint8Array(data)));
  }
  return str.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
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

function adminHtml(): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Aegis MDM Console</title>
<style>
  :root { --bg:#0f172a; --card:#1e293b; --text:#e2e8f0; --accent:#3b82f6; --danger:#ef4444; --ok:#22c55e; }
  * { box-sizing: border-box; }
  body { margin:0; font-family: system-ui, sans-serif; background:var(--bg); color:var(--text); }
  header { padding:16px 24px; background:#020617; border-bottom:1px solid #334155; display:flex; justify-content:space-between; align-items:center; }
  h1 { margin:0; font-size:1.25rem; }
  main { max-width:960px; margin:24px auto; padding:0 16px; }
  .card { background:var(--card); border-radius:12px; padding:20px; margin-bottom:20px; }
  label { display:block; font-size:0.85rem; color:#94a3b8; margin:12px 0 4px; }
  input, textarea, select { width:100%; padding:10px 12px; border-radius:8px; border:1px solid #475569; background:#0f172a; color:var(--text); }
  textarea { min-height:80px; font-family: monospace; }
  button { margin-top:12px; margin-right:8px; padding:10px 16px; border:0; border-radius:8px; background:var(--accent); color:#fff; font-weight:600; cursor:pointer; }
  button.danger { background:var(--danger); }
  button.ok { background:var(--ok); color:#052e16; }
  .row { display:flex; gap:12px; flex-wrap:wrap; }
  .row > * { flex:1; min-width:140px; }
  .status { font-size:0.9rem; color:#94a3b8; margin-top:8px; }
  .tag { display:inline-block; padding:2px 8px; border-radius:999px; background:#334155; font-size:0.75rem; margin:2px; }
  #msg { margin-top:12px; padding:10px; border-radius:8px; display:none; }
  #msg.err { display:block; background:#7f1d1d; }
  #msg.ok { display:block; background:#14532d; }
</style>
</head>
<body>
<header>
  <h1>Aegis MDM Console</h1>
  <span id="health">…</span>
</header>
<main>
  <div class="card">
    <h2>Admin access</h2>
    <label>Admin password (Worker secret ADMIN_PASSWORD)</label>
    <input id="adminPw" type="password" placeholder="Required for write operations"/>
    <button class="ok" onclick="bootstrap()">Bootstrap defaults to Firestore</button>
    <button onclick="loadAll()">Refresh from cloud</button>
    <div id="msg"></div>
  </div>

  <div class="card">
    <h2>Browser policy</h2>
    <div class="row">
      <div>
        <label>Lockdown</label>
        <select id="lockdown"><option value="false">Off</option><option value="true">On</option></select>
      </div>
      <div>
        <label>Force SafeSearch</label>
        <select id="safe"><option value="true">Yes</option><option value="false">No</option></select>
      </div>
    </div>
    <label>Allowed domains (comma-separated; empty = allow all except blocks)</label>
    <textarea id="allowed" placeholder="google.com, wikipedia.org, khanacademy.org"></textarea>
    <label>Blocked domains (comma-separated)</label>
    <textarea id="blocked" placeholder="example-bad.com"></textarea>
    <div class="row">
      <div>
        <label>Browse window start (HH:mm)</label>
        <input id="tStart" placeholder="08:00"/>
      </div>
      <div>
        <label>Browse window end (HH:mm)</label>
        <input id="tEnd" placeholder="21:00"/>
      </div>
    </div>
    <label>PAC URL</label>
    <input id="pacUrl"/>
    <button onclick="savePolicy()">Save policy</button>
    <button class="danger" onclick="setLockdown(true)">Engage lockdown</button>
    <button onclick="setLockdown(false)">Release lockdown</button>
  </div>

  <div class="card">
    <h2>Extension allowlist</h2>
    <p class="status">Only these extensions can be installed in Aegis Browser.</p>
    <div id="extList"></div>
    <label>Add extension JSON (one object)</label>
    <textarea id="extJson" placeholder='{"id":"ublock-origin","name":"uBlock Origin","amoXpiUrl":"...","chromeStoreId":"...","enabledByDefault":true}'></textarea>
    <button onclick="addExt()">Add to allowlist</button>
    <button onclick="saveExt()">Save allowlist</button>
  </div>
</main>
<script>
const API = location.origin;
let policy = {};
let extensions = { allowlist: [] };

function msg(text, ok) {
  const el = document.getElementById('msg');
  el.className = ok ? 'ok' : 'err';
  el.style.display = 'block';
  el.textContent = text;
}
function adminBody(extra={}) {
  return JSON.stringify({ adminPassword: document.getElementById('adminPw').value, ...extra });
}

async function loadHealth() {
  const r = await fetch(API + '/health');
  const j = await r.json();
  document.getElementById('health').textContent = j.project ? ('Firebase: ' + j.project) : 'ok';
}

async function loadAll() {
  const r = await fetch(API + '/policy/get');
  const j = await r.json();
  policy = j.policy || {};
  extensions = j.extensions || { allowlist: [] };
  document.getElementById('lockdown').value = String(!!policy.lockdownEnabled);
  document.getElementById('safe').value = String(policy.forceSafeSearch !== false);
  document.getElementById('allowed').value = (policy.allowedDomains || []).join(', ');
  document.getElementById('blocked').value = (policy.blockedDomains || []).join(', ');
  document.getElementById('tStart').value = policy.timeStart || '';
  document.getElementById('tEnd').value = policy.timeEnd || '';
  document.getElementById('pacUrl').value = policy.pacUrl || '';
  renderExt();
  msg('Loaded from cloud', true);
}

function renderExt() {
  const el = document.getElementById('extList');
  el.innerHTML = (extensions.allowlist || []).map((e,i) =>
    '<span class="tag">' + (e.name || e.id) +
    ' <a href="#" onclick="rmExt('+i+');return false;" style="color:#fca5a5">×</a></span>'
  ).join(' ') || '<em>empty</em>';
}

function rmExt(i) {
  extensions.allowlist.splice(i,1);
  renderExt();
}

function addExt() {
  try {
    const o = JSON.parse(document.getElementById('extJson').value);
    extensions.allowlist = extensions.allowlist || [];
    extensions.allowlist.push(o);
    renderExt();
    msg('Added locally — click Save allowlist', true);
  } catch (e) {
    msg('Invalid JSON: ' + e.message, false);
  }
}

async function savePolicy() {
  const next = {
    ...policy,
    lockdownEnabled: document.getElementById('lockdown').value === 'true',
    forceSafeSearch: document.getElementById('safe').value === 'true',
    allowedDomains: document.getElementById('allowed').value.split(',').map(s=>s.trim()).filter(Boolean),
    blockedDomains: document.getElementById('blocked').value.split(',').map(s=>s.trim()).filter(Boolean),
    timeStart: document.getElementById('tStart').value || null,
    timeEnd: document.getElementById('tEnd').value || null,
    pacUrl: document.getElementById('pacUrl').value,
  };
  const r = await fetch(API + '/policy/set', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: adminBody({ policy: next }),
  });
  const j = await r.json();
  if (!r.ok) return msg(j.error || 'Save failed', false);
  policy = j.policy;
  msg('Policy saved', true);
}

async function saveExt() {
  const r = await fetch(API + '/extensions/allowlist', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: adminBody({ allowlist: extensions.allowlist }),
  });
  const j = await r.json();
  if (!r.ok) return msg(j.error || 'Save failed', false);
  extensions = j;
  renderExt();
  msg('Allowlist saved', true);
}

async function setLockdown(on) {
  document.getElementById('lockdown').value = String(on);
  await savePolicy();
}

async function bootstrap() {
  const r = await fetch(API + '/admin/api/bootstrap', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: adminBody(),
  });
  const j = await r.json();
  if (!r.ok) return msg(j.error || 'Bootstrap failed', false);
  msg('Defaults written to Firestore', true);
  await loadAll();
}

loadHealth().then(loadAll);
</script>
</body>
</html>`;
}
