# Auth Login Registration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real Yudao App API backed member login/register session for Furniture Web, replacing the current manual-token-only path with SMS login/register, optional password login, logout, refresh token, and protected-flow refresh behavior.

**Architecture:** Keep Furniture Web as a Vue/Vite frontend that talks only to Yudao App API. Centralize auth session persistence in a small service, centralize auth requests and token refresh in `src/services/yudaoClient.js`, and keep UI state in focused auth components wired through `RhHeader.vue` and `App.vue`.

**Tech Stack:** Vue 3, Vite, browser `fetch`, Vitest, existing localStorage-based Yudao token support, Yudao App API at `/app-api/member/auth/*`.

---

## Required Reading

- `docs/yudao-integration/auth-development-prerequisites-and-constraints.md`
- `docs/yudao-integration/auth-api-contract-and-e2e-checklist.md`
- `docs/yudao-integration/local-auth-backend-db-safety-runbook.md`
- `src/services/yudaoClient.js`
- `src/components/RhHeader.vue`
- `src/components/AuthTokenPanel.vue`
- `src/App.vue`
- `src/pages/CheckoutPage.vue`
- `src/pages/OrdersPage.vue`

## File Map

- Create `src/services/authSession.js`: localStorage session read/write/clear helpers, compatibility with old `YUDAO_APP_TOKEN`, redaction helpers.
- Modify `src/services/yudaoClient.js`: auth API wrappers, request auth header, refresh-token retry, logout.
- Create `src/components/AuthSmsForm.vue`: mobile + SMS code login/register form.
- Create `src/components/AuthPasswordForm.vue`: optional mobile + password login form.
- Create `src/components/AuthModal.vue`: owns auth mode, success/error/loading state, emits auth changes.
- Modify `src/components/AuthTokenPanel.vue`: keep developer-token entry but route through unified session helpers.
- Modify `src/components/RhHeader.vue`: replace static account modal with `AuthModal`, show logged-in state and logout.
- Modify `src/App.vue`: track auth session version, refresh remote cart on login/logout.
- Modify `src/pages/CheckoutPage.vue`: reload auth-dependent data when session changes.
- Modify `src/pages/OrdersPage.vue`: reload auth-dependent data when session changes.
- Create `tests/authSession.test.js`: session storage compatibility and redaction tests.
- Create or extend `tests/yudaoAuthClient.test.js`: auth API payloads, refresh retry, logout cleanup.

## Task 1: Auth Session Service

**Files:**
- Create: `src/services/authSession.js`
- Test: `tests/authSession.test.js`

- [ ] **Step 1: Write failing session tests**

Create `tests/authSession.test.js`:

```js
import { describe, expect, it, beforeEach } from "vitest";
import {
  AUTH_SESSION_STORAGE_KEY,
  LEGACY_AUTH_TOKEN_STORAGE_KEY,
  clearYudaoSession,
  isYudaoSessionAuthenticated,
  readYudaoSession,
  redactSecret,
  writeYudaoSession,
} from "../src/services/authSession.js";

const createStorage = () => {
  const store = new Map();
  return {
    getItem: (key) => store.get(key) || null,
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
  };
};

describe("authSession", () => {
  let storage;

  beforeEach(() => {
    storage = createStorage();
  });

  it("writes and reads the Yudao auth session", () => {
    writeYudaoSession(
      {
        userId: 1024,
        accessToken: "access-token-value",
        refreshToken: "refresh-token-value",
        expiresTime: "2026-06-03T18:00:00",
      },
      storage
    );

    expect(readYudaoSession(storage)).toEqual({
      userId: 1024,
      accessToken: "access-token-value",
      refreshToken: "refresh-token-value",
      expiresTime: "2026-06-03T18:00:00",
    });
    expect(storage.getItem(LEGACY_AUTH_TOKEN_STORAGE_KEY)).toBe("access-token-value");
  });

  it("reads the legacy manual token as an authenticated session", () => {
    storage.setItem(LEGACY_AUTH_TOKEN_STORAGE_KEY, "manual-token");

    expect(readYudaoSession(storage)).toEqual({
      userId: null,
      accessToken: "manual-token",
      refreshToken: "",
      expiresTime: "",
    });
    expect(isYudaoSessionAuthenticated(storage)).toBe(true);
  });

  it("clears both session and legacy token", () => {
    storage.setItem(AUTH_SESSION_STORAGE_KEY, "{\"accessToken\":\"token\"}");
    storage.setItem(LEGACY_AUTH_TOKEN_STORAGE_KEY, "token");

    clearYudaoSession(storage);

    expect(readYudaoSession(storage)).toBe(null);
    expect(storage.getItem(LEGACY_AUTH_TOKEN_STORAGE_KEY)).toBe(null);
  });

  it("redacts secrets for logs and test output", () => {
    expect(redactSecret("abcdef123456")).toBe("abcd...3456");
    expect(redactSecret("abc")).toBe("***");
    expect(redactSecret("")).toBe("");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd "D:\code\furniture web"
npm.cmd test -- tests/authSession.test.js
```

Expected: FAIL because `src/services/authSession.js` does not exist.

- [ ] **Step 3: Implement `authSession.js`**

Create `src/services/authSession.js`:

```js
export const AUTH_SESSION_STORAGE_KEY = "YUDAO_APP_SESSION";
export const LEGACY_AUTH_TOKEN_STORAGE_KEY = "YUDAO_APP_TOKEN";

const safeStorage = (storage = globalThis.localStorage) => storage;

const normalizeSession = (session) => {
  if (!session || typeof session !== "object") return null;
  const accessToken = String(session.accessToken || "").trim();
  if (!accessToken) return null;
  return {
    userId: session.userId ?? null,
    accessToken,
    refreshToken: String(session.refreshToken || "").trim(),
    expiresTime: String(session.expiresTime || "").trim(),
  };
};

export const readYudaoSession = (storage = safeStorage()) => {
  if (!storage) return null;
  const rawSession = storage.getItem(AUTH_SESSION_STORAGE_KEY);
  if (rawSession) {
    try {
      const parsed = normalizeSession(JSON.parse(rawSession));
      if (parsed) return parsed;
    } catch {
      storage.removeItem(AUTH_SESSION_STORAGE_KEY);
    }
  }
  const legacyToken = String(storage.getItem(LEGACY_AUTH_TOKEN_STORAGE_KEY) || "").trim();
  return legacyToken
    ? { userId: null, accessToken: legacyToken, refreshToken: "", expiresTime: "" }
    : null;
};

export const writeYudaoSession = (session, storage = safeStorage()) => {
  if (!storage) return null;
  const normalized = normalizeSession(session);
  if (!normalized) {
    clearYudaoSession(storage);
    return null;
  }
  storage.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify(normalized));
  storage.setItem(LEGACY_AUTH_TOKEN_STORAGE_KEY, normalized.accessToken);
  return normalized;
};

export const clearYudaoSession = (storage = safeStorage()) => {
  if (!storage) return;
  storage.removeItem(AUTH_SESSION_STORAGE_KEY);
  storage.removeItem(LEGACY_AUTH_TOKEN_STORAGE_KEY);
};

export const isYudaoSessionAuthenticated = (storage = safeStorage()) =>
  Boolean(readYudaoSession(storage)?.accessToken);

export const redactSecret = (value) => {
  const text = String(value || "");
  if (!text) return "";
  if (text.length <= 8) return "***";
  return `${text.slice(0, 4)}...${text.slice(-4)}`;
};
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
npm.cmd test -- tests/authSession.test.js
```

Expected: PASS for all `authSession` tests.

- [ ] **Step 5: Commit**

```powershell
git add "furniture web/src/services/authSession.js" "furniture web/tests/authSession.test.js"
git commit -m "feat: add yudao auth session storage"
```

## Task 2: Yudao Auth API Wrappers

**Files:**
- Modify: `src/services/yudaoClient.js`
- Test: `tests/yudaoAuthClient.test.js`

- [ ] **Step 1: Write failing auth API tests**

Create `tests/yudaoAuthClient.test.js`:

```js
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  loginByPassword,
  loginBySms,
  logoutMember,
  refreshMemberToken,
  sendMemberSmsCode,
} from "../src/services/yudaoClient.js";
import { readYudaoSession } from "../src/services/authSession.js";

const ok = (data) => ({
  ok: true,
  json: async () => ({ code: 0, data }),
});

describe("yudao auth client", () => {
  let calls;
  let storage;

  beforeEach(() => {
    calls = [];
    storage = new Map();
    globalThis.fetch = vi.fn(async (url, options = {}) => {
      calls.push({ url, options });
      return ok({
        userId: 7,
        accessToken: "access-token",
        refreshToken: "refresh-token",
        expiresTime: "2026-06-03T18:00:00",
      });
    });
  });

  const storageAdapter = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
  };

  it("sends member sms code with scene 1", async () => {
    await sendMemberSmsCode("15601691300", { storage: storageAdapter });

    expect(calls[0].url).toContain("/member/auth/send-sms-code");
    expect(JSON.parse(calls[0].options.body)).toEqual({
      mobile: "15601691300",
      scene: 1,
    });
  });

  it("logs in by sms and stores session", async () => {
    await loginBySms({ mobile: "15601691300", code: "1234" }, { storage: storageAdapter });

    expect(calls[0].url).toContain("/member/auth/sms-login");
    expect(readYudaoSession(storageAdapter).accessToken).toBe("access-token");
  });

  it("logs in by password and stores session", async () => {
    await loginByPassword({ mobile: "15601691300", password: "admin123" }, { storage: storageAdapter });

    expect(calls[0].url).toContain("/member/auth/login");
    expect(JSON.parse(calls[0].options.body)).toEqual({
      mobile: "15601691300",
      password: "admin123",
    });
    expect(readYudaoSession(storageAdapter).refreshToken).toBe("refresh-token");
  });

  it("refreshes member token and stores the new session", async () => {
    await refreshMemberToken("old-refresh", { storage: storageAdapter });

    expect(calls[0].url).toContain("/member/auth/refresh-token?refreshToken=old-refresh");
    expect(readYudaoSession(storageAdapter).accessToken).toBe("access-token");
  });

  it("logs out and clears session even when backend succeeds", async () => {
    storageAdapter.setItem("YUDAO_APP_TOKEN", "access-token");
    await logoutMember({ storage: storageAdapter });

    expect(calls[0].url).toContain("/member/auth/logout");
    expect(calls[0].options.headers.Authorization).toBe("Bearer access-token");
    expect(readYudaoSession(storageAdapter)).toBe(null);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
npm.cmd test -- tests/yudaoAuthClient.test.js
```

Expected: FAIL because auth wrapper exports do not exist.

- [ ] **Step 3: Add auth wrappers to `yudaoClient.js`**

Modify `src/services/yudaoClient.js` to import session helpers:

```js
import {
  clearYudaoSession,
  readYudaoSession,
  writeYudaoSession,
  LEGACY_AUTH_TOKEN_STORAGE_KEY,
} from "./authSession.js";
```

Replace the old token key export with compatibility:

```js
export const AUTH_TOKEN_STORAGE_KEY = LEGACY_AUTH_TOKEN_STORAGE_KEY;
```

Add auth functions:

```js
const authStorage = (options = {}) => options.storage || globalThis.localStorage;

export const sendMemberSmsCode = (mobile, options = {}) =>
  requestYudao("/member/auth/send-sms-code", {
    ...options,
    method: "POST",
    body: JSON.stringify({ mobile, scene: 1 }),
  });

const persistLoginResponse = (data, options = {}) => writeYudaoSession(data, authStorage(options));

export const loginBySms = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/sms-login", {
    ...options,
    method: "POST",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const loginByPassword = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/login", {
    ...options,
    method: "POST",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const refreshMemberToken = async (refreshToken, options = {}) => {
  const data = await requestYudao(`/member/auth/refresh-token?refreshToken=${encodeURIComponent(refreshToken)}`, {
    ...options,
    method: "POST",
    token: "",
  });
  return persistLoginResponse(data, options);
};

export const logoutMember = async (options = {}) => {
  try {
    await requestYudao("/member/auth/logout", {
      ...options,
      method: "POST",
    });
  } finally {
    clearYudaoSession(authStorage(options));
  }
};
```

- [ ] **Step 4: Update token read/write compatibility**

Keep these exports but route through unified session:

```js
export const readYudaoToken = (storage = globalThis.localStorage) =>
  readYudaoSession(storage)?.accessToken || "";

export const writeYudaoToken = (token, storage = globalThis.localStorage) => {
  const nextToken = String(token || "").trim();
  if (nextToken) {
    writeYudaoSession({ accessToken: nextToken, refreshToken: "", expiresTime: "", userId: null }, storage);
  } else {
    clearYudaoSession(storage);
  }
};
```

- [ ] **Step 5: Run tests**

Run:

```powershell
npm.cmd test -- tests/authSession.test.js tests/yudaoAuthClient.test.js
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add "furniture web/src/services/yudaoClient.js" "furniture web/tests/yudaoAuthClient.test.js"
git commit -m "feat: add yudao member auth api"
```

## Task 3: Token Refresh Retry

**Files:**
- Modify: `src/services/yudaoClient.js`
- Test: `tests/yudaoAuthClient.test.js`

- [ ] **Step 1: Add failing refresh retry test**

Append to `tests/yudaoAuthClient.test.js`:

```js
it("refreshes token and retries once when an authenticated request fails with 401 result", async () => {
  storageAdapter.setItem(
    "YUDAO_APP_SESSION",
    JSON.stringify({
      userId: 7,
      accessToken: "expired-token",
      refreshToken: "refresh-token",
      expiresTime: "2026-06-03T18:00:00",
    })
  );

  globalThis.fetch = vi
    .fn()
    .mockResolvedValueOnce({ ok: true, json: async () => ({ code: 401, msg: "未登录" }) })
    .mockResolvedValueOnce(ok({
      userId: 7,
      accessToken: "new-token",
      refreshToken: "new-refresh",
      expiresTime: "2026-06-03T19:00:00",
    }))
    .mockResolvedValueOnce(ok({ validList: [], invalidList: [] }));

  const { getRemoteCartItems } = await import("../src/services/yudaoClient.js");
  await expect(getRemoteCartItems({ storage: storageAdapter })).resolves.toEqual([]);

  expect(globalThis.fetch).toHaveBeenCalledTimes(3);
  expect(readYudaoSession(storageAdapter).accessToken).toBe("new-token");
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
npm.cmd test -- tests/yudaoAuthClient.test.js
```

Expected: FAIL because `requestYudao` does not refresh and retry.

- [ ] **Step 3: Implement single retry in `requestYudao`**

Refactor `requestYudao`:

```js
const AUTH_FAILURE_CODES = new Set([401, 1002011000]);

const isAuthFailurePayload = (payload) =>
  payload && typeof payload === "object" && AUTH_FAILURE_CODES.has(Number(payload.code));

export const requestYudao = async (path, options = {}) => {
  const base = options.baseUrl || getYudaoAppApiBase();
  const session = readYudaoSession(authStorage(options));
  const token = options.token !== undefined ? options.token : session?.accessToken;
  const headers = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(`${base}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    throw new Error(`Yudao HTTP ${response.status}`);
  }

  const payload = await response.json();
  if (isAuthFailurePayload(payload) && session?.refreshToken && !options.skipAuthRetry) {
    await refreshMemberToken(session.refreshToken, options);
    return requestYudao(path, { ...options, skipAuthRetry: true });
  }

  return unwrapYudaoResult(payload);
};
```

- [ ] **Step 4: Run tests**

Run:

```powershell
npm.cmd test -- tests/yudaoAuthClient.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add "furniture web/src/services/yudaoClient.js" "furniture web/tests/yudaoAuthClient.test.js"
git commit -m "feat: refresh yudao member token on auth failure"
```

## Task 4: Auth UI Components

**Files:**
- Create: `src/components/AuthSmsForm.vue`
- Create: `src/components/AuthPasswordForm.vue`
- Create: `src/components/AuthModal.vue`
- Modify: `src/components/RhHeader.vue`
- Modify: `src/styles.css`

- [ ] **Step 1: Create `AuthSmsForm.vue`**

Create a focused SMS form:

```vue
<script setup>
import { computed, ref } from "vue";
import { loginBySms, sendMemberSmsCode } from "../services/yudaoClient.js";

const emit = defineEmits(["authenticated"]);
const mobile = ref("");
const code = ref("");
const error = ref("");
const busy = ref(false);
const sending = ref(false);
const cooldown = ref(0);

const canSend = computed(() => /^1\d{10}$/.test(mobile.value) && !sending.value && cooldown.value === 0);
const canSubmit = computed(() => /^1\d{10}$/.test(mobile.value) && /^\d{4,6}$/.test(code.value) && !busy.value);

const startCooldown = () => {
  cooldown.value = 60;
  const timer = window.setInterval(() => {
    cooldown.value -= 1;
    if (cooldown.value <= 0) window.clearInterval(timer);
  }, 1000);
};

const sendCode = async () => {
  if (!canSend.value) return;
  sending.value = true;
  error.value = "";
  try {
    await sendMemberSmsCode(mobile.value);
    startCooldown();
  } catch (err) {
    error.value = err.message || "Unable to send verification code.";
  } finally {
    sending.value = false;
  }
};

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  error.value = "";
  try {
    const session = await loginBySms({ mobile: mobile.value, code: code.value });
    emit("authenticated", session);
  } catch (err) {
    error.value = err.message || "Unable to sign in.";
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <form class="auth-form" @submit.prevent="submit">
    <label>
      <span>Mobile</span>
      <input v-model.trim="mobile" autocomplete="tel" inputmode="tel" type="tel" />
    </label>
    <label>
      <span>Verification Code</span>
      <div class="auth-code-row">
        <input v-model.trim="code" autocomplete="one-time-code" inputmode="numeric" type="text" />
        <button type="button" :disabled="!canSend" @click="sendCode">
          {{ cooldown ? `${cooldown}s` : sending ? "Sending..." : "Send" }}
        </button>
      </div>
    </label>
    <p v-if="error" class="auth-error">{{ error }}</p>
    <button type="submit" :disabled="!canSubmit">{{ busy ? "Working..." : "SIGN IN / REGISTER" }}</button>
  </form>
</template>
```

- [ ] **Step 2: Create `AuthPasswordForm.vue`**

Create optional password login:

```vue
<script setup>
import { computed, ref } from "vue";
import { loginByPassword } from "../services/yudaoClient.js";

const emit = defineEmits(["authenticated"]);
const mobile = ref("");
const password = ref("");
const error = ref("");
const busy = ref(false);
const canSubmit = computed(() => /^1\d{10}$/.test(mobile.value) && password.value.length >= 4 && password.value.length <= 16 && !busy.value);

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  error.value = "";
  try {
    const session = await loginByPassword({ mobile: mobile.value, password: password.value });
    emit("authenticated", session);
  } catch {
    error.value = "Mobile or password is incorrect.";
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <form class="auth-form" @submit.prevent="submit">
    <label>
      <span>Mobile</span>
      <input v-model.trim="mobile" autocomplete="tel" inputmode="tel" type="tel" />
    </label>
    <label>
      <span>Password</span>
      <input v-model="password" autocomplete="current-password" type="password" />
    </label>
    <p v-if="error" class="auth-error">{{ error }}</p>
    <button type="submit" :disabled="!canSubmit">{{ busy ? "Working..." : "SIGN IN" }}</button>
  </form>
</template>
```

- [ ] **Step 3: Create `AuthModal.vue`**

Create modal shell:

```vue
<script setup>
import { computed, ref } from "vue";
import { clearYudaoSession, readYudaoSession } from "../services/authSession.js";
import { logoutMember } from "../services/yudaoClient.js";
import AuthPasswordForm from "./AuthPasswordForm.vue";
import AuthSmsForm from "./AuthSmsForm.vue";
import AuthTokenPanel from "./AuthTokenPanel.vue";

const props = defineProps({
  open: { type: Boolean, default: false },
});
const emit = defineEmits(["close", "auth-change"]);
const mode = ref("sms");
const session = ref(readYudaoSession());
const isAuthenticated = computed(() => Boolean(session.value?.accessToken));

const handleAuthenticated = (nextSession) => {
  session.value = nextSession;
  emit("auth-change", nextSession);
};

const logout = async () => {
  await logoutMember();
  clearYudaoSession();
  session.value = null;
  emit("auth-change", null);
};
</script>

<template>
  <div v-if="open" class="account-modal-layer" role="presentation">
    <section class="account-modal" role="dialog" aria-modal="true" aria-labelledby="account-modal-title">
      <button class="account-modal-close" type="button" aria-label="Close sign in" @click="emit('close')">
        <span></span>
        <span></span>
      </button>
      <h2 id="account-modal-title">{{ isAuthenticated ? "ACCOUNT" : "SIGN IN" }}</h2>
      <template v-if="isAuthenticated">
        <p>Signed in as member {{ session.userId || "with developer token" }}</p>
        <button type="button" @click="logout">SIGN OUT</button>
      </template>
      <template v-else>
        <p>Use your mobile number to sign in. First-time mobile numbers are registered automatically after verification.</p>
        <div class="auth-mode-tabs">
          <button type="button" :class="{ active: mode === 'sms' }" @click="mode = 'sms'">Code</button>
          <button type="button" :class="{ active: mode === 'password' }" @click="mode = 'password'">Password</button>
        </div>
        <AuthSmsForm v-if="mode === 'sms'" @authenticated="handleAuthenticated" />
        <AuthPasswordForm v-else @authenticated="handleAuthenticated" />
        <AuthTokenPanel @token-change="session = readYudaoSession(); emit('auth-change', session)" />
      </template>
    </section>
  </div>
</template>
```

- [ ] **Step 4: Wire `RhHeader.vue`**

Import `AuthModal` and replace inline modal block:

```js
import AuthModal from "./AuthModal.vue";
```

Add emit:

```js
const emit = defineEmits(["open-cart", "auth-change"]);
```

Replace the old account modal template with:

```vue
<AuthModal
  :open="accountOpen"
  @close="closeAccount"
  @auth-change="emit('auth-change', $event)"
/>
```

- [ ] **Step 5: Add CSS**

Add styles to `src/styles.css`:

```css
.auth-form {
  display: grid;
  gap: 14px;
}

.auth-form label {
  display: grid;
  gap: 6px;
  font-size: 11px;
  letter-spacing: 0;
  text-transform: uppercase;
}

.auth-code-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.auth-error {
  color: #8f1d1d;
  font-size: 12px;
}

.auth-mode-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 16px 0;
}

.auth-mode-tabs button.active {
  border-color: #111;
  background: #111;
  color: #fff;
}
```

- [ ] **Step 6: Run tests and manual smoke**

Run:

```powershell
npm.cmd test
npm.cmd run build -- --outDir harness/phase-b/.tmp-dist --emptyOutDir
```

Expected: tests pass and build succeeds.

- [ ] **Step 7: Commit**

```powershell
git add "furniture web/src/components/AuthSmsForm.vue" "furniture web/src/components/AuthPasswordForm.vue" "furniture web/src/components/AuthModal.vue" "furniture web/src/components/RhHeader.vue" "furniture web/src/styles.css"
git commit -m "feat: add member auth modal"
```

## Task 5: App-Level Auth Refresh Hooks

**Files:**
- Modify: `src/App.vue`
- Modify: `src/pages/CheckoutPage.vue`
- Modify: `src/pages/OrdersPage.vue`

- [ ] **Step 1: Wire auth change in `App.vue`**

Add:

```js
const authVersion = ref(0);

const handleAuthChange = async () => {
  authVersion.value += 1;
  await loadRemoteCart();
};
```

Update template:

```vue
<RhHeader
  v-model:page="currentPage"
  :cart-count="cartQuantity"
  :cart-mode="cartMode"
  @auth-change="handleAuthChange"
  @open-cart="cartOpen = true"
/>
```

Pass auth version:

```vue
<component
  :is="pageComponent"
  :auth-version="authVersion"
  :items="cartItems"
  @add-to-cart="addToCart"
  @order-created="openOrderDetail"
/>
```

- [ ] **Step 2: Reload checkout on auth changes**

In `CheckoutPage.vue`, add prop:

```js
authVersion: {
  type: Number,
  default: 0,
},
```

Add watcher:

```js
watch(() => props.authVersion, loadCheckoutData);
```

Update imports:

```js
import { computed, onMounted, ref, watch } from "vue";
```

- [ ] **Step 3: Reload orders on auth changes**

In `OrdersPage.vue`, add prop:

```js
const props = defineProps({
  authVersion: {
    type: Number,
    default: 0,
  },
});
```

Add watcher:

```js
watch(() => props.authVersion, loadOrders);
```

Update imports:

```js
import { computed, onMounted, ref, watch } from "vue";
```

- [ ] **Step 4: Run tests**

Run:

```powershell
npm.cmd test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add "furniture web/src/App.vue" "furniture web/src/pages/CheckoutPage.vue" "furniture web/src/pages/OrdersPage.vue"
git commit -m "feat: refresh commerce data after auth changes"
```

## Task 6: Security and Boundary Verification

**Files:**
- Modify: `harness/phase-b/boundary-allowlist.txt` if new files need allowlisting
- No business code changes unless checks reveal a defect

- [ ] **Step 1: Run unit tests**

```powershell
cd "D:\code\furniture web"
npm.cmd test
```

Expected: all tests pass.

- [ ] **Step 2: Run build**

```powershell
npm.cmd run build -- --outDir harness/phase-b/.tmp-dist --emptyOutDir
```

Expected: build succeeds.

- [ ] **Step 3: Run boundary harness**

```powershell
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1
```

Expected: harness passes. If it fails because new auth files are not allowlisted, update `harness/phase-b/boundary-allowlist.txt` with exact new paths and rerun.

- [ ] **Step 4: Search for forbidden database and secret strings**

Run:

```powershell
rg -n "jdbc:|mysql://|redis://|MYSQL_ROOT_PASSWORD|NACOS_PASSWORD|password=|accessToken|refreshToken" src tests docs/yudao-integration
```

Expected:

- No database connection strings in `src/` or `tests/`.
- `accessToken` and `refreshToken` appear only in auth service code, test fake data, and docs.
- No real token values.

- [ ] **Step 5: Manual E2E checklist**

Use `docs/yudao-integration/auth-api-contract-and-e2e-checklist.md` and record:

- SMS send request appears in Network.
- SMS login request appears in Network.
- Token saves to localStorage.
- `/trade/cart/list` sends `Authorization`.
- Logout clears localStorage.
- Backend 48080 stopped produces auth service unavailable.

- [ ] **Step 6: Commit final verification changes**

```powershell
git add "furniture web/harness/phase-b/boundary-allowlist.txt"
git commit -m "test: allow auth files in phase b harness"
```

Only run this commit step if the allowlist actually changed.

## Final Acceptance

The implementation is ready for review when:

- `npm.cmd test` passes.
- Temporary build passes.
- Phase B harness passes.
- Manual auth API checklist is complete.
- No frontend code or tests contain database credentials or direct database clients.
- No logs, screenshots, fixtures, or docs contain real tokens, real passwords, real verification codes, or production member data.
