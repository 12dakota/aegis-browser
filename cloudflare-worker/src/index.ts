/**
 * Aegis API Worker — auth, per-child policy, extension allowlist.
 * Admin UI lives on Cloudflare Pages (separate origin).
 *
 * Firestore:
 *   parents/{parentUid}
 *   parents/{parentUid}/children/{childUid}
 *   parents/{parentUid}/children/{childUid}/settings/policy
 *   parents/{parentUid}/children/{childUid}/settings/extensions
 *   aegis/aegis_config_policy  (global fallback)
 *   aegis/aegis_config_extensions
 */

export interface Env {
  FIREBASE_API_KEY?: string;
  FIREBASE_PROJECT_ID?: string;
  FIREBASE_CLIENT_EMAIL?: string;
  FIREBASE_PRIVATE_KEY?: string;
  AEGIS_SIGNING_SECRET: string;
  ADMIN_PASSWORD?: string;
  ADMIN_ORIGIN?: string; // e.g. https://aegis-admin.pages.dev
}

const KNOWN_EXTENSIONS: Record<string, any> = {
  "ublock-origin": {
    id: "ublock-origin",
    name: "uBlock Origin",
    description: "Efficient wide-spectrum content blocker",
    amoId: "uBlock0@raymondhill.net",
    amoXpiUrl: "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
    chromeStoreId: "cjpalhdlnbpafilfilklakntgclifmdh",
    chromeStoreUrl: "https://chromewebstore.google.com/detail/ublock-origin/cjpalhdlnbpafilfilklakntgclifmdh",
    enabledByDefault: true,
  },
  "ublock": {
    id: "ublock-origin",
    name: "uBlock Origin",
    description: "Efficient wide-spectrum content blocker",
    amoId: "uBlock0@raymondhill.net",
    amoXpiUrl: "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
    chromeStoreId: "cjpalhdlnbpafilfilklakntgclifmdh",
    chromeStoreUrl: "https://chromewebstore.google.com/detail/ublock-origin/cjpalhdlnbpafilfilklakntgclifmdh",
    enabledByDefault: true,
  },
  "privacy-badger": {
    id: "privacy-badger",
    name: "Privacy Badger",
    description: "Blocks invisible trackers",
    amoId: "jid1-MnnxcxisBPnSXQ@jetpack",
    amoXpiUrl: "https://addons.mozilla.org/firefox/downloads/latest/privacy-badger17/latest.xpi",
    chromeStoreId: "pkehgijcmpdhfbdbbnkijodmdjhbjlgp",
    chromeStoreUrl: "https://chromewebstore.google.com/detail/privacy-badger/pkehgijcmpdhfbdbbnkijodmdjhbjlgp",
    enabledByDefault: false,
  },
  "https-everywhere": {
    id: "https-everywhere",
    name: "HTTPS Everywhere",
    description: "Encrypt the web (legacy / similar tools)",
    amoId: "https-everywhere@eff.org",
    amoXpiUrl: "https://addons.mozilla.org/firefox/downloads/latest/https-everywhere/latest.xpi",
    chromeStoreId: "gcbommkclmfnmhdhafbkaknmecfkjckg",
    chromeStoreUrl: "https://chromewebstore.google.com/detail/https-everywhere/gcbommkclmfnmhdhafbkaknmecfkjckg",
    enabledByDefault: false,
  },
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(env, request) });
    }

    try {
      if (path === "/health") {
        return json(env, request, {
          status: "ok",
          service: "aegis-worker",
          firebaseConfigured: Boolean(env.FIREBASE_API_KEY),
          project: env.FIREBASE_PROJECT_ID || null,
          mdm: true,
          adminOrigin: env.ADMIN_ORIGIN || null,
        });
      }

      // Auth
      if (path === "/auth/login" && request.method === "POST") return await handleLogin(request, env);
      if (path === "/auth/register" && request.method === "POST") return await handleRegister(request, env);
      if (path === "/auth/validate" && request.method === "POST") return await handleValidate(request, env);

      // Parent console APIs (session required)
      if (path === "/parent/me" && request.method === "GET") return await handleParentMe(request, env);
      if (path === "/parent/children" && request.method === "GET") return await handleListChildren(request, env);
      if (path === "/parent/children" && request.method === "POST") return await handleCreateChild(request, env);
      if (path === "/parent/children/delete" && request.method === "POST") return await handleDeleteChild(request, env);

      // Per-child policy / extensions
      if (path === "/child/policy" && request.method === "GET") return await handleChildPolicyGet(request, env, url);
      if (path === "/child/policy" && request.method === "POST") return await handleChildPolicySet(request, env);
      if (path === "/child/extensions" && request.method === "GET") return await handleChildExtGet(request, env, url);
      if (path === "/child/extensions" && request.method === "POST") return await handleChildExtSet(request, env);
      if (path === "/child/extensions/add" && request.method === "POST") return await handleChildExtAdd(request, env);
      if (path === "/child/extensions/remove" && request.method === "POST") return await handleChildExtRemove(request, env);
      if (path === "/child/lockdown" && request.method === "POST") return await handleChildLockdown(request, env);

      // Device-facing (child app) — by email/userId after login
      if (path === "/policy/get" && request.method === "GET") return await handleDevicePolicyGet(request, env, url);
      if (path === "/extensions/allowlist" && request.method === "GET") return await handleDeviceExtGet(request, env, url);
      if (path === "/extensions/catalog" && request.method === "GET") {
        return json(env, request, { catalog: Object.values(KNOWN_EXTENSIONS).filter((e, i, a) => a.findIndex(x => x.id === e.id) === i) });
      }

      // Legacy global admin password bootstrap
      if (path === "/admin/api/bootstrap" && request.method === "POST") return await handleBootstrap(request, env);

      // Redirect old /admin to Pages if configured
      if (path === "/admin" || path === "/admin/") {
        const dest = env.ADMIN_ORIGIN || "https://aegis-admin.pages.dev";
        return Response.redirect(dest, 302);
      }

      return json(env, request, { error: "Not found" }, 404);
    } catch (e: any) {
      return json(env, request, { error: e.message || "Internal error" }, 500);
    }
  },
};

// ---------- Auth ----------

async function handleLogin(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { email?: string; password?: string };
  if (!body.email || !body.password) return json(env, request, { error: "email and password required" }, 400);
  if (!env.FIREBASE_API_KEY) return json(env, request, { error: "Firebase not configured" }, 503);

  const resp = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${env.FIREBASE_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: body.email, password: body.password, returnSecureToken: true }),
    }
  );
  const data = (await resp.json()) as any;
  if (!resp.ok) {
    const msg = data.error?.message || "Login failed";
    return json(env, request, { error: friendlyAuthError(msg), code: msg }, 401);
  }

  const userId = data.localId as string;
  const email = (data.email || body.email) as string;
  // Ensure parent profile exists (first login as parent console user)
  await ensureParent(env, userId, email);

  const sessionToken = await mintSessionToken(env, userId, email, Number(data.expiresIn) || 3600);
  return json(env, request, {
    sessionToken,
    userId,
    email,
    expiresIn: String(data.expiresIn || 3600),
    role: "parent",
  });
}

async function handleRegister(request: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_API_KEY) return json(env, request, { error: "Firebase not configured" }, 503);
  const body = (await request.json()) as { email?: string; password?: string; displayName?: string; role?: string };
  if (!body.email || !body.password) return json(env, request, { error: "email and password required" }, 400);
  if (body.password.length < 6) return json(env, request, { error: "password must be at least 6 characters" }, 400);

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
  if (!resp.ok) return json(env, request, { error: data.error?.message || "Register failed" }, 400);

  const userId = data.localId as string;
  const email = (data.email || body.email) as string;
  await ensureParent(env, userId, email);

  const sessionToken = await mintSessionToken(env, userId, email, Number(data.expiresIn) || 3600);
  return json(env, request, { sessionToken, userId, email, expiresIn: String(data.expiresIn || 3600), role: "parent" });
}

async function handleValidate(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { sessionToken?: string };
  if (!body.sessionToken) return json(env, request, { error: "sessionToken required" }, 400);
  const payload = await verifySessionTokenPayload(env, body.sessionToken);
  return json(env, request, { valid: Boolean(payload), userId: payload?.sub || null, email: payload?.email || null });
}

// ---------- Parent / children ----------

async function requireParent(request: Request, env: Env): Promise<{ userId: string; email: string } | Response> {
  const auth = request.headers.get("Authorization") || "";
  if (!auth.startsWith("Bearer ")) return json(env, request, { error: "Missing Authorization Bearer sessionToken" }, 401);
  const payload = await verifySessionTokenPayload(env, auth.slice(7));
  if (!payload?.sub) return json(env, request, { error: "Invalid or expired session" }, 401);
  return { userId: payload.sub, email: payload.email || "" };
}

async function handleParentMe(request: Request, env: Env): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const doc = await firestoreGet(env, `parents_${parent.userId}`) || { email: parent.email, userId: parent.userId };
  return json(env, request, { parent: { userId: parent.userId, email: parent.email, ...doc } });
}

async function handleListChildren(request: Request, env: Env): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const children = await listChildren(env, parent.userId);
  return json(env, request, { children });
}

async function handleCreateChild(request: Request, env: Env): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  if (!env.FIREBASE_API_KEY) return json(env, request, { error: "Firebase not configured" }, 503);

  const body = (await request.json()) as {
    email?: string;
    password?: string;
    displayName?: string;
  };
  if (!body.email || !body.password) return json(env, request, { error: "child email and password required" }, 400);
  if (body.password.length < 6) return json(env, request, { error: "password must be at least 6 characters" }, 400);

  // Create Firebase Auth user for the child
  const resp = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${env.FIREBASE_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: body.email,
        password: body.password,
        displayName: body.displayName || body.email.split("@")[0],
        returnSecureToken: true,
      }),
    }
  );
  const data = (await resp.json()) as any;
  if (!resp.ok) return json(env, request, { error: data.error?.message || "Could not create child account" }, 400);

  const childId = data.localId as string;
  const child = {
    userId: childId,
    email: data.email || body.email,
    displayName: body.displayName || body.email.split("@")[0],
    parentId: parent.userId,
    createdAt: new Date().toISOString(),
  };
  await firestoreSet(env, `parents_${parent.userId}_children_${childId}`, child);
  await firestoreSet(env, `parents_${parent.userId}_children_${childId}_settings_policy`, defaultPolicy());
  await firestoreSet(env, `parents_${parent.userId}_children_${childId}_settings_extensions`, defaultExtensions());
  await firestoreSet(env, `child_index_${childId}`, { parentId: parent.userId, email: child.email });
  await appendChildId(env, parent.userId, childId);

  return json(env, request, { status: "ok", child });
}

async function handleDeleteChild(request: Request, env: Env): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const body = (await request.json()) as { childId?: string };
  if (!body.childId) return json(env, request, { error: "childId required" }, 400);
  // Soft-delete marker (Auth user deletion needs Admin SDK; mark inactive)
  await firestoreSet(env, `parents_${parent.userId}_children_${body.childId}`, {
    userId: body.childId,
    inactive: true,
    deletedAt: new Date().toISOString(),
  });
  return json(env, request, { status: "ok" });
}

async function listChildren(env: Env, parentId: string): Promise<any[]> {
  // Firestore list collection group is hard via REST without query; store children list on parent doc
  const parentDoc = (await firestoreGet(env, `parents_${parentId}`)) || {};
  const ids: string[] = parentDoc.childIds || [];
  const out: any[] = [];
  for (const id of ids) {
    const c = await firestoreGet(env, `parents_${parentId}_children_${id}`);
    if (c && !c.inactive) out.push(c);
  }
  return out;
}

async function ensureParent(env: Env, userId: string, email: string) {
  const existing = await firestoreGet(env, `parents_${userId}`);
  if (existing) return;
  await firestoreSet(env, `parents_${userId}`, {
    userId,
    email,
    childIds: [],
    createdAt: new Date().toISOString(),
  });
}

async function appendChildId(env: Env, parentId: string, childId: string) {
  const parent = (await firestoreGet(env, `parents_${parentId}`)) || { userId: parentId, childIds: [] };
  const ids: string[] = Array.isArray(parent.childIds) ? parent.childIds : [];
  if (!ids.includes(childId)) ids.push(childId);
  parent.childIds = ids;
  await firestoreSet(env, `parents_${parentId}`, parent);
}

// Patch create child to append id
async function handleCreateChildWrapped(request: Request, env: Env): Promise<Response> {
  // replaced inline below - keep handleCreateChild and call append after set
  return handleCreateChild(request, env);
}

// ---------- Per-child policy ----------

async function assertParentOwnsChild(env: Env, parentId: string, childId: string): Promise<boolean> {
  const c = await firestoreGet(env, `parents_${parentId}_children_${childId}`);
  return Boolean(c && !c.inactive);
}

async function handleChildPolicyGet(request: Request, env: Env, url: URL): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const childId = url.searchParams.get("childId") || "";
  if (!childId) return json(env, request, { error: "childId required" }, 400);
  if (!(await assertParentOwnsChild(env, parent.userId, childId))) {
    return json(env, request, { error: "Child not found" }, 404);
  }
  const policy = (await firestoreGet(env, `parents_${parent.userId}_children_${childId}_settings_policy`)) || defaultPolicy();
  return json(env, request, { policy });
}

async function handleChildPolicySet(request: Request, env: Env): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const body = (await request.json()) as { childId?: string; policy?: any };
  if (!body.childId || !body.policy) return json(env, request, { error: "childId and policy required" }, 400);
  if (!(await assertParentOwnsChild(env, parent.userId, body.childId))) {
    return json(env, request, { error: "Child not found" }, 404);
  }
  const current = (await firestoreGet(env, `parents_${parent.userId}_children_${body.childId}_settings_policy`)) || defaultPolicy();
  const next = { ...current, ...body.policy, updatedAt: new Date().toISOString() };
  await firestoreSet(env, `parents_${parent.userId}_children_${body.childId}_settings_policy`, next);
  return json(env, request, { status: "ok", policy: next });
}

async function handleChildExtGet(request: Request, env: Env, url: URL): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const childId = url.searchParams.get("childId") || "";
  if (!childId) return json(env, request, { error: "childId required" }, 400);
  const doc = (await firestoreGet(env, `parents_${parent.userId}_children_${childId}_settings_extensions`)) || defaultExtensions();
  return json(env, request, doc);
}

async function handleChildExtSet(request: Request, env: Env): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const body = (await request.json()) as { childId?: string; allowlist?: any[] };
  if (!body.childId) return json(env, request, { error: "childId required" }, 400);
  const doc = { allowlist: body.allowlist || [], updatedAt: new Date().toISOString() };
  await firestoreSet(env, `parents_${parent.userId}_children_${body.childId}_settings_extensions`, doc);
  return json(env, request, { status: "ok", ...doc });
}

/** Add by name or id only — resolves against known catalog */
async function handleChildExtAdd(request: Request, env: Env): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const body = (await request.json()) as { childId?: string; name?: string; id?: string; query?: string };
  if (!body.childId) return json(env, request, { error: "childId required" }, 400);
  const q = (body.query || body.id || body.name || "").trim().toLowerCase();
  if (!q) return json(env, request, { error: "Provide extension name or id" }, 400);

  let entry = KNOWN_EXTENSIONS[q];
  if (!entry) {
    entry = Object.values(KNOWN_EXTENSIONS).find(
      (e) => e.id === q || e.name.toLowerCase() === q || e.name.toLowerCase().includes(q) || e.id.includes(q)
    );
  }
  if (!entry) {
    // Allow custom id as minimal entry (parent responsibility)
    entry = {
      id: q.replace(/\s+/g, "-"),
      name: body.name || body.query || q,
      description: "Custom allowlisted extension",
      amoId: "",
      amoXpiUrl: "",
      chromeStoreId: "",
      chromeStoreUrl: "",
      enabledByDefault: true,
    };
  }

  const path = `parents_${parent.userId}_children_${body.childId}_settings_extensions`;
  const doc = (await firestoreGet(env, path)) || defaultExtensions();
  const list = Array.isArray(doc.allowlist) ? doc.allowlist : [];
  if (list.some((x: any) => x.id === entry.id)) {
    return json(env, request, { status: "ok", message: "Already on allowlist", allowlist: list });
  }
  list.push(entry);
  const next = { allowlist: list, updatedAt: new Date().toISOString() };
  await firestoreSet(env, path, next);
  return json(env, request, { status: "ok", added: entry, ...next });
}

async function handleChildExtRemove(request: Request, env: Env): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const body = (await request.json()) as { childId?: string; id?: string };
  if (!body.childId || !body.id) return json(env, request, { error: "childId and id required" }, 400);
  const path = `parents_${parent.userId}_children_${body.childId}_settings_extensions`;
  const doc = (await firestoreGet(env, path)) || defaultExtensions();
  const list = (doc.allowlist || []).filter((x: any) => x.id !== body.id);
  const next = { allowlist: list, updatedAt: new Date().toISOString() };
  await firestoreSet(env, path, next);
  return json(env, request, { status: "ok", ...next });
}

async function handleChildLockdown(request: Request, env: Env): Promise<Response> {
  const parent = await requireParent(request, env);
  if (parent instanceof Response) return parent;
  const body = (await request.json()) as { childId?: string; enable?: boolean; reason?: string };
  if (!body.childId) return json(env, request, { error: "childId required" }, 400);
  const path = `parents_${parent.userId}_children_${body.childId}_settings_policy`;
  const policy = (await firestoreGet(env, path)) || defaultPolicy();
  policy.lockdownEnabled = body.enable ?? true;
  policy.lockdownReason = body.reason || "Parent remote command";
  policy.updatedAt = new Date().toISOString();
  await firestoreSet(env, path, policy);
  return json(env, request, { status: "ok", policy });
}

// ---------- Device (child app) ----------

async function handleDevicePolicyGet(request: Request, env: Env, url: URL): Promise<Response> {
  // Optional: Authorization session from child login, or ?userId=
  const userId = url.searchParams.get("userId") || "";
  let parentId = "";
  let childId = userId;

  const auth = request.headers.get("Authorization") || "";
  if (auth.startsWith("Bearer ")) {
    const payload = await verifySessionTokenPayload(env, auth.slice(7));
    if (payload?.sub) {
      childId = payload.sub;
    }
  }

  if (childId) {
    const idx = await firestoreGet(env, `child_index_${childId}`);
    if (idx?.parentId) {
      parentId = idx.parentId;
      const policy = (await firestoreGet(env, `parents_${parentId}_children_${childId}_settings_policy`)) || defaultPolicy();
      const extensions = (await firestoreGet(env, `parents_${parentId}_children_${childId}_settings_extensions`)) || defaultExtensions();
      return json(env, request, { policy, extensions, scope: "child" });
    }
  }

  // Global fallback
  const policy = (await firestoreGet(env, "aegis_aegis_config_policy")) || defaultPolicy();
  const extensions = (await firestoreGet(env, "aegis_aegis_config_extensions")) || defaultExtensions();
  return json(env, request, { policy, extensions, scope: "global" });
}

async function handleDeviceExtGet(request: Request, env: Env, url: URL): Promise<Response> {
  const r = await handleDevicePolicyGet(request, env, url);
  const data = await r.json();
  return json(env, request, data.extensions || defaultExtensions());
}

async function handleBootstrap(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { adminPassword?: string };
  if (env.ADMIN_PASSWORD && body.adminPassword !== env.ADMIN_PASSWORD) {
    return json(env, request, { error: "Unauthorized" }, 401);
  }
  const policy = defaultPolicy();
  const extensions = defaultExtensions();
  await firestoreSet(env, "aegis_aegis_config_policy", policy);
  await firestoreSet(env, "aegis_aegis_config_extensions", extensions);
  return json(env, request, { status: "bootstrapped", policy, extensions });
}

function defaultPolicy() {
  return {
    lockdownEnabled: false,
    lockdownReason: "",
    forceSystemProxy: true,
    pacUrl: "https://pac.cloudflare-gateway.com/e2950b77bc288e9366bebbcccf1c25a8/WIyYdBdISzKaPnuqVMMyqg",
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
    allowlist: [KNOWN_EXTENSIONS["ublock-origin"]],
    updatedAt: new Date().toISOString(),
  };
}

function friendlyAuthError(msg: string): string {
  if (msg === "EMAIL_NOT_FOUND" || msg === "INVALID_EMAIL") return "No account found for that email";
  if (msg === "INVALID_PASSWORD" || msg === "INVALID_LOGIN_CREDENTIALS") return "Incorrect email or password";
  if (msg === "USER_DISABLED") return "This account has been disabled";
  if (msg === "TOO_MANY_ATTEMPTS_TRY_LATER") return "Too many attempts — try again later";
  if (msg === "EMAIL_EXISTS") return "Email already registered";
  return msg;
}

// ---------- Firestore ----------

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
  const sig = await crypto.subtle.sign({ name: "RSASSA-PKCS1-v1_5" }, key, new TextEncoder().encode(unsigned));
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
  const b64 = pem.replace(/-----BEGIN PRIVATE KEY-----/, "").replace(/-----END PRIVATE KEY-----/, "").replace(/\s+/g, "");
  const raw = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey("pkcs8", raw, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
}

function toFirestoreName(projectId: string, path: string): string {
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
  if (!resp.ok) return null;
  return fromFirestoreFields(data.fields || {});
}

async function firestoreSet(env: Env, path: string, obj: any): Promise<void> {
  const project = env.FIREBASE_PROJECT_ID || "ominium";
  const token = await getAccessToken(env);
  if (!token) throw new Error("No service account — cannot write Firestore");
  const name = toFirestoreName(project, path);
  const body = { fields: toFirestoreFields(obj) };
  const resp = await fetch(`https://firestore.googleapis.com/v1/${name}`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    const err = await resp.text();
    throw new Error("Firestore write failed: " + err);
  }
}

function toFirestoreFields(obj: any): any {
  const fields: any = {};
  for (const [k, v] of Object.entries(obj)) fields[k] = toFirestoreValue(v);
  return fields;
}

function toFirestoreValue(v: any): any {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(toFirestoreValue) } };
  if (typeof v === "object") return { mapValue: { fields: toFirestoreFields(v) } };
  return { stringValue: String(v) };
}

function fromFirestoreFields(fields: any): any {
  const out: any = {};
  for (const [k, v] of Object.entries(fields || {})) out[k] = fromFirestoreValue(v);
  return out;
}

function fromFirestoreValue(v: any): any {
  if (v.nullValue !== undefined) return null;
  if (v.booleanValue !== undefined) return v.booleanValue;
  if (v.integerValue !== undefined) return Number(v.integerValue);
  if (v.doubleValue !== undefined) return v.doubleValue;
  if (v.stringValue !== undefined) return v.stringValue;
  if (v.arrayValue) return (v.arrayValue.values || []).map(fromFirestoreValue);
  if (v.mapValue) return fromFirestoreFields(v.mapValue.fields || {});
  return null;
}

// ---------- Tokens ----------

async function mintSessionToken(env: Env, userId: string, email: string, expiresIn: number): Promise<string> {
  const secret = env.AEGIS_SIGNING_SECRET || "aegis-dev-signing-secret-change-me";
  const exp = Math.floor(Date.now() / 1000) + expiresIn;
  const payload = btoa(JSON.stringify({ sub: userId, email, exp }));
  const sig = await hmacSign(secret, payload);
  return `aegis.${payload}.${sig}`;
}

async function verifySessionTokenPayload(env: Env, token: string): Promise<{ sub: string; email?: string; exp?: number } | null> {
  try {
    const parts = token.split(".");
    if (parts.length !== 3 || parts[0] !== "aegis") return null;
    const secret = env.AEGIS_SIGNING_SECRET || "aegis-dev-signing-secret-change-me";
    const expected = await hmacSign(secret, parts[1]);
    if (expected !== parts[2]) return null;
    const payload = JSON.parse(atob(parts[1]));
    if (payload.exp && payload.exp < Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch {
    return null;
  }
}

async function hmacSign(secret: string, data: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(data));
  return btoa(String.fromCharCode(...new Uint8Array(sig)));
}

function b64url(data: ArrayBuffer | string): string {
  let str: string;
  if (typeof data === "string") str = btoa(data);
  else str = btoa(String.fromCharCode(...new Uint8Array(data)));
  return str.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function json(env: Env, request: Request, data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders(env, request) },
  });
}

function corsHeaders(env: Env, request?: Request): Record<string, string> {
  const origin = request?.headers.get("Origin") || "*";
  const allowed = env.ADMIN_ORIGIN || "*";
  const allow =
    allowed === "*" || origin === allowed || origin.endsWith(".pages.dev") || origin.endsWith(".workers.dev")
      ? origin === "null"
        ? "*"
        : origin
      : allowed;
  return {
    "Access-Control-Allow-Origin": allow === "null" ? "*" : allow,
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Allow-Credentials": "true",
  };
}
