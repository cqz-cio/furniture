# Yudao Furniture Commerce Phase B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Phase B commerce loop where Yudao-managed products feed the furniture storefront, remote cart items can be settled and turned into Yudao orders, and users can view basic order information.

**Architecture:** Keep Furniture Web as a standalone Vue/Vite storefront and treat Yudao as the system of record through App API only. Add focused service helpers for address, settlement, order creation, and order mapping; route new checkout/order pages through the existing lightweight path router in `src/App.vue`.

**Tech Stack:** Vue 3, Vite, Vitest, browser `fetch`, browser `localStorage`, Yudao App API.

---

## Scope

Phase B implements:

- Yudao token bridge for integration testing.
- Address list/default address loading.
- Checkout page with local preview and Yudao settlement.
- Yudao order creation from remote cart items.
- Order list and order detail page.
- Tests for all new data shaping and request payload helpers.

Phase B does not implement:

- Real payment.
- Full member registration/login.
- Backend Java changes.
- Yudao Admin UI changes.
- Coupons, points, seckill, combination, after-sale.

## File Structure

Create:

- `src/services/checkoutSession.js`: pure checkout payload and eligibility helpers.
- `src/pages/CheckoutPage.vue`: checkout workflow page.
- `src/pages/OrdersPage.vue`: order list and detail page.
- `src/components/AuthTokenPanel.vue`: token save/clear panel in account modal or checkout.
- `tests/checkoutSession.test.js`: pure helper tests.

Modify:

- `src/services/yudaoClient.js`: add address, settlement, create order, order list/detail API methods and mappers.
- `src/App.vue`: add `/checkout` and `/orders` route entries; pass cart state into checkout.
- `src/components/CartDrawer.vue`: emit checkout action.
- `src/components/RhHeader.vue`: show token panel entry in account modal.
- `src/i18n.js`: add checkout/order/token labels.
- `src/styles.css`: add compact checkout and order page styles.
- `tests/integrationModels.test.js`: extend Yudao mapper tests.

Reference only:

- `D:\code\yudao电商管理平台前后端\yudao-cloud\yudao-module-mall\yudao-module-trade-server\src\main\java\cn\iocoder\yudao\module\trade\controller\app\order\AppTradeOrderController.java`
- `D:\code\yudao电商管理平台前后端\yudao-cloud\yudao-module-member\yudao-module-member-server\src\main\java\cn\iocoder\yudao\module\member\controller\app\address\AppAddressController.java`

## Task 1: Checkout Session Helpers

**Files:**

- Create: `src/services/checkoutSession.js`
- Test: `tests/checkoutSession.test.js`

- [ ] **Step 1: Write the failing tests**

Create `tests/checkoutSession.test.js`:

```javascript
import { describe, expect, it } from "vitest";
import {
  buildLocalCheckoutSummary,
  buildYudaoOrderPayload,
  canUseYudaoCheckout,
  getCheckoutMode,
} from "../src/services/checkoutSession.js";

describe("checkout session helpers", () => {
  const yudaoItems = [
    { skuId: 11, cartId: 101, quantity: 2, price: 1200, source: "yudao", name: "Sofa" },
    { skuId: 12, cartId: 102, quantity: 1, price: 400, source: "yudao", name: "Chair" },
  ];

  it("allows yudao checkout only when every item has a remote cart id", () => {
    expect(canUseYudaoCheckout(yudaoItems)).toBe(true);
    expect(canUseYudaoCheckout([{ ...yudaoItems[0], cartId: undefined }])).toBe(false);
    expect(canUseYudaoCheckout([{ ...yudaoItems[0], source: "demo" }])).toBe(false);
  });

  it("builds yudao order payload from cart ids, address, and delivery type", () => {
    expect(buildYudaoOrderPayload(yudaoItems, { addressId: 2001 })).toEqual({
      items: [
        { skuId: 11, count: 2, cartId: 101 },
        { skuId: 12, count: 1, cartId: 102 },
      ],
      pointStatus: false,
      deliveryType: 1,
      addressId: 2001,
      remark: "",
    });
  });

  it("summarizes local checkout totals without remote order data", () => {
    expect(buildLocalCheckoutSummary(yudaoItems)).toEqual({
      quantity: 3,
      subtotal: 2800,
      items: yudaoItems,
    });
  });

  it("reports checkout mode from cart source and token state", () => {
    expect(getCheckoutMode(yudaoItems, "token")).toBe("yudao");
    expect(getCheckoutMode(yudaoItems, "")).toBe("token-required");
    expect(getCheckoutMode([{ ...yudaoItems[0], source: "demo" }], "token")).toBe("local-preview");
    expect(getCheckoutMode([], "token")).toBe("empty");
  });
});
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
npm.cmd test tests/checkoutSession.test.js
```

Expected: FAIL because `src/services/checkoutSession.js` does not exist.

- [ ] **Step 3: Add minimal implementation**

Create `src/services/checkoutSession.js`:

```javascript
import { getCartTotals } from "./localCart.js";

export const DEFAULT_DELIVERY_TYPE = 1;

export const canUseYudaoCheckout = (items) =>
  items.length > 0 && items.every((item) => item.source === "yudao" && item.cartId && item.skuId);

export const buildYudaoOrderPayload = (items, options = {}) => ({
  items: items.map((item) => ({
    skuId: item.skuId,
    count: item.quantity,
    cartId: item.cartId,
  })),
  pointStatus: false,
  deliveryType: options.deliveryType || DEFAULT_DELIVERY_TYPE,
  addressId: options.addressId,
  remark: options.remark || "",
});

export const buildLocalCheckoutSummary = (items) => ({
  ...getCartTotals(items),
  items,
});

export const getCheckoutMode = (items, token) => {
  if (items.length === 0) return "empty";
  if (!canUseYudaoCheckout(items)) return "local-preview";
  return token ? "yudao" : "token-required";
};
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```powershell
npm.cmd test tests/checkoutSession.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit task**

Run:

```powershell
git add src/services/checkoutSession.js tests/checkoutSession.test.js
git commit -m "test: add checkout session helpers"
```

If the user has not asked for commits, skip the commit command and keep the changes staged only when explicitly requested.

## Task 2: Yudao Address and Order Client

**Files:**

- Modify: `src/services/yudaoClient.js`
- Modify: `tests/integrationModels.test.js`

- [ ] **Step 1: Write failing mapper and payload tests**

Append to `tests/integrationModels.test.js`:

```javascript
import {
  mapAddressResponse,
  mapOrderDetail,
  mapOrderPage,
  mapSettlementResponse,
} from "../src/services/yudaoClient.js";

it("maps yudao address responses for checkout selection", () => {
  expect(mapAddressResponse({ id: 9, name: "Ada", mobile: "15500000000", areaName: "Shanghai", detailAddress: "Road 1" })).toEqual({
    id: 9,
    name: "Ada",
    mobile: "15500000000",
    areaName: "Shanghai",
    detailAddress: "Road 1",
    label: "Ada · 15500000000 · Shanghai Road 1",
  });
});

it("maps settlement totals and items from yudao order settlement response", () => {
  const settlement = mapSettlementResponse({
    price: { payPrice: 259900, totalPrice: 329900, deliveryPrice: 0 },
    items: [{ skuId: 88, count: 1, spuName: "Cloud Sofa", picUrl: "cover.jpg", payPrice: 259900 }],
  });

  expect(settlement).toEqual({
    payPrice: 2599,
    totalPrice: 3299,
    deliveryPrice: 0,
    items: [{ skuId: 88, count: 1, name: "Cloud Sofa", cover: "cover.jpg", payPrice: 2599 }],
    raw: expect.any(Object),
  });
});

it("maps order page and detail responses into storefront orders", () => {
  const page = mapOrderPage({
    list: [{ id: 1, no: "O1", status: 10, payPrice: 120000, items: [{ spuName: "Sofa" }] }],
    total: 1,
  });
  const detail = mapOrderDetail({ id: 1, no: "O1", status: 10, payPrice: 120000, payOrderId: 99, items: [] });

  expect(page.total).toBe(1);
  expect(page.list[0]).toMatchObject({ id: 1, no: "O1", payPrice: 1200 });
  expect(detail).toMatchObject({ id: 1, no: "O1", payPrice: 1200, payOrderId: 99 });
});
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
npm.cmd test tests/integrationModels.test.js
```

Expected: FAIL because the new mapper exports are missing.

- [ ] **Step 3: Add mapper and API functions**

Modify `src/services/yudaoClient.js` by adding:

```javascript
export const mapAddressResponse = (address = {}) => ({
  id: address.id,
  name: address.name || "",
  mobile: address.mobile || "",
  areaName: address.areaName || "",
  detailAddress: address.detailAddress || "",
  label: [address.name, address.mobile, `${address.areaName || ""} ${address.detailAddress || ""}`.trim()]
    .filter(Boolean)
    .join(" · "),
  raw: address,
});

export const mapSettlementResponse = (settlement = {}) => ({
  payPrice: fenToYuan(settlement.price?.payPrice ?? settlement.payPrice),
  totalPrice: fenToYuan(settlement.price?.totalPrice ?? settlement.totalPrice),
  deliveryPrice: fenToYuan(settlement.price?.deliveryPrice ?? settlement.deliveryPrice),
  items: (settlement.items || []).map((item) => ({
    skuId: item.skuId,
    count: Number(item.count) || 1,
    name: item.spuName || item.name || "Product",
    cover: item.picUrl || item.cover || "",
    payPrice: fenToYuan(item.payPrice ?? item.price),
  })),
  raw: settlement,
});

export const mapOrderDetail = (order = {}) => ({
  id: order.id,
  no: order.no || String(order.id || ""),
  status: order.status,
  payStatus: Boolean(order.payStatus),
  payPrice: fenToYuan(order.payPrice),
  payOrderId: order.payOrderId,
  createTime: order.createTime,
  items: (order.items || []).map((item) => ({
    id: item.id,
    skuId: item.skuId,
    name: item.spuName || item.name || "Product",
    cover: item.picUrl || item.cover || "",
    count: Number(item.count) || 1,
    price: fenToYuan(item.price ?? item.payPrice),
  })),
  raw: order,
});

export const mapOrderPage = (page = {}) => ({
  list: (page.list || []).map(mapOrderDetail),
  total: Number(page.total || 0),
});

export const getDefaultAddress = async (options = {}) => {
  const data = await requestYudao("/member/address/get-default", options);
  return data ? mapAddressResponse(data) : null;
};

export const getAddressList = async (options = {}) => {
  const data = await requestYudao("/member/address/list", options);
  return (data || []).map(mapAddressResponse);
};

export const settleOrder = async (payload, options = {}) => {
  const search = new URLSearchParams();
  payload.items.forEach((item, index) => {
    search.append(`items[${index}].skuId`, item.skuId);
    search.append(`items[${index}].count`, item.count);
    search.append(`items[${index}].cartId`, item.cartId);
  });
  search.append("pointStatus", String(payload.pointStatus));
  search.append("deliveryType", String(payload.deliveryType));
  if (payload.addressId) search.append("addressId", String(payload.addressId));
  const data = await requestYudao(`/trade/order/settlement?${search}`, options);
  return mapSettlementResponse(data);
};

export const createOrder = async (payload, options = {}) => {
  const data = await requestYudao("/trade/order/create", {
    ...options,
    method: "POST",
    body: JSON.stringify(payload),
  });
  return data;
};

export const getOrderPage = async (params = {}, options = {}) => {
  const search = new URLSearchParams({ pageNo: "1", pageSize: "10", ...params });
  const data = await requestYudao(`/trade/order/page?${search}`, options);
  return mapOrderPage(data);
};

export const getOrderDetail = async (id, options = {}) => {
  const data = await requestYudao(`/trade/order/get-detail?id=${encodeURIComponent(id)}`, options);
  return data ? mapOrderDetail(data) : null;
};
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```powershell
npm.cmd test tests/integrationModels.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit task**

Run:

```powershell
git add src/services/yudaoClient.js tests/integrationModels.test.js
git commit -m "feat: map yudao checkout and order data"
```

Skip commit unless explicitly requested.

## Task 3: Auth Token Panel

**Files:**

- Create: `src/components/AuthTokenPanel.vue`
- Modify: `src/components/RhHeader.vue`
- Modify: `src/i18n.js`
- Modify: `src/styles.css`

- [ ] **Step 1: Write failing storage tests**

Append to `tests/integrationModels.test.js`:

```javascript
import { AUTH_TOKEN_STORAGE_KEY, readYudaoToken, writeYudaoToken } from "../src/services/yudaoClient.js";

it("reads and writes the yudao app token using the shared storage key", () => {
  const storage = new Map();
  const fakeStorage = {
    getItem: (key) => storage.get(key),
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key),
  };

  writeYudaoToken(" abc ", fakeStorage);
  expect(storage.get(AUTH_TOKEN_STORAGE_KEY)).toBe("abc");
  expect(readYudaoToken(fakeStorage)).toBe("abc");

  writeYudaoToken("", fakeStorage);
  expect(storage.has(AUTH_TOKEN_STORAGE_KEY)).toBe(false);
});
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
npm.cmd test tests/integrationModels.test.js
```

Expected: FAIL because token helper exports are missing.

- [ ] **Step 3: Add token helpers**

Modify `src/services/yudaoClient.js`:

```javascript
export const AUTH_TOKEN_STORAGE_KEY = "YUDAO_APP_TOKEN";

export const readYudaoToken = (storage = globalThis.localStorage) => storage?.getItem(AUTH_TOKEN_STORAGE_KEY) || "";

export const writeYudaoToken = (token, storage = globalThis.localStorage) => {
  if (!storage) return;
  const nextToken = String(token || "").trim();
  if (nextToken) {
    storage.setItem(AUTH_TOKEN_STORAGE_KEY, nextToken);
  } else {
    storage.removeItem(AUTH_TOKEN_STORAGE_KEY);
  }
};
```

Update `requestYudao` to call `readYudaoToken()` instead of directly reading localStorage.

- [ ] **Step 4: Create `AuthTokenPanel.vue`**

Create:

```vue
<script setup>
import { ref } from "vue";
import { readYudaoToken, writeYudaoToken } from "../services/yudaoClient.js";

const emit = defineEmits(["token-change"]);
const token = ref(readYudaoToken());

const save = () => {
  writeYudaoToken(token.value);
  emit("token-change", token.value.trim());
};

const clear = () => {
  token.value = "";
  writeYudaoToken("");
  emit("token-change", "");
};
</script>

<template>
  <section class="auth-token-panel" aria-label="Yudao token">
    <label>
      <span>Yudao App Token</span>
      <input v-model="token" autocomplete="off" type="password" />
    </label>
    <div class="auth-token-actions">
      <button type="button" @click="save">Save Token</button>
      <button type="button" @click="clear">Clear</button>
    </div>
  </section>
</template>
```

- [ ] **Step 5: Mount panel in account modal**

Modify `src/components/RhHeader.vue`:

```javascript
import AuthTokenPanel from "./AuthTokenPanel.vue";
```

Add inside `.account-modal` after the sign-in form:

```vue
<AuthTokenPanel />
```

- [ ] **Step 6: Add compact styles**

Add to `src/styles.css`:

```css
.auth-token-panel {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(0, 0, 0, 0.14);
}

.auth-token-panel label,
.auth-token-actions {
  display: grid;
  gap: 8px;
}

.auth-token-panel input {
  width: 100%;
  height: 38px;
  border: 1px solid rgba(0, 0, 0, 0.22);
  padding: 0 10px;
}

.auth-token-actions {
  grid-template-columns: 1fr 1fr;
}
```

- [ ] **Step 7: Run tests**

Run:

```powershell
npm.cmd test
```

Expected: PASS.

## Task 4: Checkout Route and Cart Checkout Action

**Files:**

- Modify: `src/App.vue`
- Modify: `src/components/CartDrawer.vue`
- Modify: `src/pages/CheckoutPage.vue`
- Modify: `src/i18n.js`

- [ ] **Step 1: Add route behavior test for helper only**

Append to `tests/checkoutSession.test.js`:

```javascript
import { getCheckoutReturnPath } from "../src/services/checkoutSession.js";

it("returns the checkout route used by the cart drawer", () => {
  expect(getCheckoutReturnPath()).toBe("/checkout");
});
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
npm.cmd test tests/checkoutSession.test.js
```

Expected: FAIL because `getCheckoutReturnPath` is missing.

- [ ] **Step 3: Add helper**

Add to `src/services/checkoutSession.js`:

```javascript
export const getCheckoutReturnPath = () => "/checkout";
```

- [ ] **Step 4: Create initial checkout page**

Create `src/pages/CheckoutPage.vue`:

```vue
<script setup>
import { computed } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { buildLocalCheckoutSummary, getCheckoutMode } from "../services/checkoutSession.js";
import { readYudaoToken } from "../services/yudaoClient.js";

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
});

const summary = computed(() => buildLocalCheckoutSummary(props.items));
const mode = computed(() => getCheckoutMode(props.items, readYudaoToken()));
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
</script>

<template>
  <section class="checkout-page">
    <header class="checkout-head">
      <p class="eyebrow">Checkout</p>
      <h1>Review Your Order</h1>
      <p v-if="mode === 'token-required'">Add a Yudao App token before creating a remote order.</p>
      <p v-else-if="mode === 'local-preview'">Demo cart items can be reviewed locally but cannot create a Yudao order.</p>
      <p v-else-if="mode === 'empty'">Your bag is empty.</p>
    </header>

    <section class="checkout-grid">
      <div class="checkout-items">
        <article v-for="item in items" :key="item.skuId" class="checkout-item">
          <ProductImage :src="item.cover" :label="item.name" />
          <div>
            <h2>{{ item.name }}</h2>
            <p>{{ item.quantity }} × {{ money(item.price) }}</p>
          </div>
        </article>
      </div>
      <aside class="checkout-summary">
        <span>Subtotal</span>
        <strong>{{ money(summary.subtotal) }}</strong>
      </aside>
    </section>
  </section>
</template>
```

- [ ] **Step 5: Wire checkout route**

Modify `src/App.vue`:

```javascript
import CheckoutPage from "./pages/CheckoutPage.vue";
```

Add route:

```javascript
checkout: "/checkout",
```

Add component selection:

```javascript
if (currentPage.value === "checkout") return CheckoutPage;
```

Pass items:

```vue
<component :is="pageComponent" :items="cartItems" @add-to-cart="addToCart" />
```

- [ ] **Step 6: Make cart drawer checkout button navigate**

Modify `src/components/CartDrawer.vue`:

```javascript
const emit = defineEmits(["checkout", "close", "update-quantity", "remove"]);
```

Change checkout button:

```vue
<button type="button" :disabled="items.length === 0" @click="emit('checkout')">{{ t("checkout") }}</button>
```

Modify `src/App.vue` CartDrawer usage:

```vue
@checkout="currentPage = 'checkout'; cartOpen = false"
```

- [ ] **Step 7: Run tests**

Run:

```powershell
npm.cmd test
```

Expected: PASS.

## Task 5: Remote Settlement and Create Order

**Files:**

- Modify: `src/pages/CheckoutPage.vue`
- Modify: `src/services/checkoutSession.js`
- Modify: `src/services/yudaoClient.js`
- Modify: `src/App.vue`
- Modify: `src/i18n.js`

- [ ] **Step 1: Add test for selecting default address id**

Append to `tests/checkoutSession.test.js`:

```javascript
import { getSelectedAddressId } from "../src/services/checkoutSession.js";

it("uses selected address first and then default address", () => {
  expect(getSelectedAddressId(9, { id: 8 })).toBe(9);
  expect(getSelectedAddressId(undefined, { id: 8 })).toBe(8);
  expect(getSelectedAddressId(undefined, null)).toBe(undefined);
});
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
npm.cmd test tests/checkoutSession.test.js
```

Expected: FAIL because `getSelectedAddressId` is missing.

- [ ] **Step 3: Add helper**

Add to `src/services/checkoutSession.js`:

```javascript
export const getSelectedAddressId = (selectedAddressId, defaultAddress) => selectedAddressId || defaultAddress?.id;
```

- [ ] **Step 4: Implement checkout remote state**

Modify `src/pages/CheckoutPage.vue` to import:

```javascript
import { onMounted, ref } from "vue";
import {
  buildYudaoOrderPayload,
  canUseYudaoCheckout,
  getSelectedAddressId,
} from "../services/checkoutSession.js";
import {
  createOrder,
  getAddressList,
  getDefaultAddress,
  readYudaoToken,
  settleOrder,
} from "../services/yudaoClient.js";
```

Add state:

```javascript
const emit = defineEmits(["order-created"]);
const addresses = ref([]);
const defaultAddress = ref(null);
const selectedAddressId = ref(undefined);
const settlement = ref(null);
const error = ref("");
const busy = ref(false);

const loadCheckoutData = async () => {
  if (!canUseYudaoCheckout(props.items) || !readYudaoToken()) return;
  busy.value = true;
  error.value = "";
  try {
    addresses.value = await getAddressList();
    defaultAddress.value = await getDefaultAddress();
    selectedAddressId.value = getSelectedAddressId(selectedAddressId.value, defaultAddress.value);
    if (selectedAddressId.value) {
      const payload = buildYudaoOrderPayload(props.items, { addressId: selectedAddressId.value });
      settlement.value = await settleOrder(payload);
    }
  } catch (err) {
    error.value = err.message;
  } finally {
    busy.value = false;
  }
};

const submitOrder = async () => {
  const addressId = getSelectedAddressId(selectedAddressId.value, defaultAddress.value);
  if (!addressId) {
    error.value = "No Yudao address is available for this user.";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const payload = buildYudaoOrderPayload(props.items, { addressId });
    const result = await createOrder(payload);
    emit("order-created", result.id);
  } catch (err) {
    error.value = err.message;
  } finally {
    busy.value = false;
  }
};

onMounted(loadCheckoutData);
```

Add UI controls:

```vue
<section v-if="addresses.length" class="checkout-addresses">
  <label>
    Ship To
    <select v-model.number="selectedAddressId" @change="loadCheckoutData">
      <option v-for="address in addresses" :key="address.id" :value="address.id">{{ address.label }}</option>
    </select>
  </label>
</section>

<p v-if="error" class="checkout-error">{{ error }}</p>

<button type="button" :disabled="busy || mode !== 'yudao'" @click="submitOrder">
  Create Yudao Order
</button>
```

- [ ] **Step 5: Route successful orders**

Modify `src/App.vue`:

```vue
@order-created="currentPage = 'orders'; window.history.pushState({ page: 'orders' }, '', `/orders?id=${$event}`)"
```

If inline template expression becomes too dense, create a method:

```javascript
const openOrderDetail = (orderId) => {
  currentPage.value = "orders";
  window.history.pushState({ page: "orders" }, "", `/orders?id=${orderId}`);
};
```

Use:

```vue
@order-created="openOrderDetail"
```

- [ ] **Step 6: Run tests**

Run:

```powershell
npm.cmd test
```

Expected: PASS.

## Task 6: Orders Page

**Files:**

- Create: `src/pages/OrdersPage.vue`
- Modify: `src/App.vue`
- Modify: `src/i18n.js`
- Modify: `src/styles.css`

- [ ] **Step 1: Add route helper test**

Append to `tests/checkoutSession.test.js`:

```javascript
import { getOrderDetailPath } from "../src/services/checkoutSession.js";

it("builds order detail route with query id", () => {
  expect(getOrderDetailPath(12)).toBe("/orders?id=12");
});
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
npm.cmd test tests/checkoutSession.test.js
```

Expected: FAIL because `getOrderDetailPath` is missing.

- [ ] **Step 3: Add helper**

Add to `src/services/checkoutSession.js`:

```javascript
export const getOrderDetailPath = (id) => `/orders?id=${encodeURIComponent(id)}`;
```

- [ ] **Step 4: Create orders page**

Create `src/pages/OrdersPage.vue`:

```vue
<script setup>
import { computed, onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { getOrderDetail, getOrderPage, readYudaoToken } from "../services/yudaoClient.js";

const loading = ref(true);
const error = ref("");
const orders = ref([]);
const total = ref(0);
const detail = ref(null);
const orderId = computed(() => new URLSearchParams(window.location.search).get("id"));
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const loadOrders = async () => {
  loading.value = true;
  error.value = "";
  try {
    if (!readYudaoToken()) {
      error.value = "Add a Yudao App token to view orders.";
      return;
    }
    if (orderId.value) {
      detail.value = await getOrderDetail(orderId.value);
    }
    const page = await getOrderPage({ pageNo: 1, pageSize: 10 });
    orders.value = page.list;
    total.value = page.total;
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
};

onMounted(loadOrders);
</script>

<template>
  <section class="orders-page">
    <header class="orders-head">
      <p class="eyebrow">Orders</p>
      <h1>Order History</h1>
      <p v-if="total">{{ total }} orders</p>
    </header>

    <p v-if="loading" class="product-loading">Loading orders...</p>
    <p v-if="error" class="checkout-error">{{ error }}</p>

    <article v-if="detail" class="order-detail-card">
      <h2>{{ detail.no }}</h2>
      <p>Status: {{ detail.status }}</p>
      <strong>{{ money(detail.payPrice) }}</strong>
      <div v-for="item in detail.items" :key="item.id || item.skuId" class="checkout-item">
        <ProductImage :src="item.cover" :label="item.name" />
        <div>
          <h3>{{ item.name }}</h3>
          <p>{{ item.count }} × {{ money(item.price) }}</p>
        </div>
      </div>
    </article>

    <section class="order-list">
      <a v-for="order in orders" :key="order.id" class="order-row" :href="`/orders?id=${order.id}`">
        <span>{{ order.no }}</span>
        <span>Status {{ order.status }}</span>
        <strong>{{ money(order.payPrice) }}</strong>
      </a>
    </section>
  </section>
</template>
```

- [ ] **Step 5: Wire route**

Modify `src/App.vue`:

```javascript
import OrdersPage from "./pages/OrdersPage.vue";
```

Add route:

```javascript
orders: "/orders",
```

Add component selection:

```javascript
if (currentPage.value === "orders") return OrdersPage;
```

- [ ] **Step 6: Run tests**

Run:

```powershell
npm.cmd test
```

Expected: PASS.

## Task 7: Final Verification and Harness

**Files:**

- Modify: `docs/yudao-integration/development-guide.md` only if implementation revealed a concrete command or API correction.

- [ ] **Step 1: Run unit tests**

Run:

```powershell
npm.cmd test
```

Expected: all test files pass.

- [ ] **Step 2: Run temporary build**

Run:

```powershell
npm.cmd run build -- --outDir harness/phase-b/.tmp-dist --emptyOutDir
```

Expected: Vite exits with code 0 and writes only to `harness/phase-b/.tmp-dist`.

- [ ] **Step 3: Remove temporary build output**

Run:

```powershell
if (Test-Path "harness/phase-b/.tmp-dist") { Remove-Item -LiteralPath "harness/phase-b/.tmp-dist" -Recurse -Force }
```

Expected: temporary directory is removed.

- [ ] **Step 4: Run boundary harness**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1 -SkipBuild
```

Expected: no boundary violations.

- [ ] **Step 5: Manual smoke test**

Run:

```powershell
npm.cmd run dev
```

Open the local Vite URL and verify:

- `/sofas-plp` displays products.
- `/sofa-pdp?id=<existing product id>` displays product detail.
- Add to cart opens the cart drawer.
- Checkout opens `/checkout`.
- With no token, checkout says token is required.
- With a valid Yudao App token and default address, checkout can create an order.
- `/orders` lists orders.
- `/orders?id=<created order id>` shows order detail.

## Self-Review

Spec coverage:

- Product/catalog integration is covered by preserving existing `yudaoClient.js`, PLP, and PDP behavior.
- Cart integration is covered by existing local/remote cart services and Task 4 checkout routing.
- Checkout/order creation is covered by Tasks 1, 2, 5.
- Order list/detail is covered by Task 6.
- Safety and harness rules are covered by `docs/yudao-integration/code-boundary-safety.md` and `harness/phase-b`.

Placeholder scan:

- This plan intentionally contains no placeholder implementation steps.

Type consistency:

- Checkout item fields use existing cart item fields: `skuId`, `cartId`, `quantity`, `price`, `source`.
- Yudao order payload fields match `AppTradeOrderSettlementReqVO`: `items`, `pointStatus`, `deliveryType`, `addressId`, `remark`.
- Delivery type uses Yudao `DeliveryTypeEnum.EXPRESS = 1`.

