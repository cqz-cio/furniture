import assert from "node:assert/strict";

const mode = process.env.WISHLIST_SMOKE_MODE || "mock";
const baseUrl = (process.env.YUDAO_SMOKE_BASE_URL || "http://127.0.0.1:48080/app-api").replace(/\/$/, "");
const tenantId = process.env.YUDAO_SMOKE_TENANT_ID || "121";
const token = process.env.YUDAO_SMOKE_TOKEN || "";

const smokeItem = {
  spuId: 910001,
  skuId: 91000101,
  count: 1,
  spuName: "Wishlist Smoke Oak Chair",
  picUrl: "/assets/smoke/wishlist-chair.webp",
  price: 89900,
  marketPrice: 129900,
  color: "Wheat",
  fabric: "Performance linen",
  width: "84W",
  dimensions: "84W x 38D x 32H",
  delivery: "Smoke delivery window",
};

const ok = (data) => ({ code: 0, data, msg: "" });

const parseBody = (body) => (typeof body === "string" ? JSON.parse(body || "{}") : body || {});

const isDocumentationDomain = (hostname = "") => {
  const normalized = String(hostname || "").trim().toLowerCase();
  return normalized === "example.com" || normalized.endsWith(".example.com") || normalized.endsWith(".example");
};

const isLocalhost = (hostname = "") => {
  const normalized = String(hostname || "").trim().toLowerCase();
  return normalized === "localhost" || normalized === "127.0.0.1" || normalized === "0.0.0.0" || normalized === "::1" || normalized === "[::1]";
};

const validateLiveSmokeConfig = () => {
  if (mode !== "live") return;

  if (!token) {
    throw new Error("YUDAO_SMOKE_TOKEN is required when WISHLIST_SMOKE_MODE=live");
  }

  const normalizedToken = token.toLowerCase();
  if (
    normalizedToken.includes("<") ||
    normalizedToken.includes(">") ||
    normalizedToken.includes("replace-me") ||
    normalizedToken.includes("your-token")
  ) {
    throw new Error("YUDAO_SMOKE_TOKEN must be a real live token");
  }

  let url;
  try {
    url = new URL(baseUrl);
  } catch {
    throw new Error("YUDAO_SMOKE_BASE_URL must be an absolute http(s) URL");
  }

  if (!["http:", "https:"].includes(url.protocol)) {
    throw new Error("YUDAO_SMOKE_BASE_URL must be an absolute http(s) URL");
  }
  if (isLocalhost(url.hostname)) {
    throw new Error("YUDAO_SMOKE_BASE_URL must not point to localhost");
  }
  if (isDocumentationDomain(url.hostname)) {
    throw new Error("YUDAO_SMOKE_BASE_URL must not use a documentation/example domain");
  }
};

const createMockClient = () => {
  let rows = [];

  return async (path, options = {}) => {
    const method = options.method || "GET";
    const body = parseBody(options.body);

    if (path === "/product/favorite/create" && method === "POST") {
      assert.equal(body.spuId, smokeItem.spuId);
      assert.equal(body.skuId, smokeItem.skuId);
      rows = [{ id: 1, ...body }];
      return 1;
    }

    if (path.startsWith("/product/favorite/page") && method === "GET") {
      return { total: rows.length, list: rows };
    }

    if (path === "/product/favorite/update-count" && method === "PUT") {
      assert.deepEqual(body, { spuId: smokeItem.spuId, skuId: smokeItem.skuId, count: 3 });
      rows = rows.map((row) =>
        row.spuId === body.spuId && row.skuId === body.skuId ? { ...row, count: body.count } : row,
      );
      return true;
    }

    if (path === "/product/favorite/delete" && method === "DELETE") {
      assert.deepEqual(body, { spuId: smokeItem.spuId, skuId: smokeItem.skuId });
      rows = rows.filter((row) => row.spuId !== body.spuId || row.skuId !== body.skuId);
      return true;
    }

    throw new Error(`Unexpected wishlist smoke request: ${method} ${path}`);
  };
};

const liveRequest = async (path, options = {}) => {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "tenant-id": tenantId,
      Authorization: `Bearer ${token}`,
      ...(options.headers || {}),
    },
  });
  const payload = await response.json().catch(() => null);

  if (!response.ok || payload?.code !== 0) {
    throw new Error(`Yudao wishlist smoke failed: ${options.method || "GET"} ${path} ${JSON.stringify(payload)}`);
  }

  return payload.data;
};

validateLiveSmokeConfig();

const request = mode === "live" ? liveRequest : createMockClient();

const favoritePagePath = "/product/favorite/page?pageNo=1&pageSize=50";
const findSmokeRow = (page) =>
  (page?.list || []).find((row) => row.spuId === smokeItem.spuId && row.skuId === smokeItem.skuId);

await request("/product/favorite/create", {
  method: "POST",
  body: JSON.stringify(smokeItem),
});

const createdPage = await request(favoritePagePath);
assert.equal(findSmokeRow(createdPage)?.count, 1, "created wishlist row should be visible on page");

await request("/product/favorite/update-count", {
  method: "PUT",
  body: JSON.stringify({ spuId: smokeItem.spuId, skuId: smokeItem.skuId, count: 3 }),
});

const updatedPage = await request(favoritePagePath);
assert.equal(findSmokeRow(updatedPage)?.count, 3, "updated wishlist row count should be visible on page");

await request("/product/favorite/delete", {
  method: "DELETE",
  body: JSON.stringify({ spuId: smokeItem.spuId, skuId: smokeItem.skuId }),
});

const deletedPage = await request(favoritePagePath);
assert.equal(findSmokeRow(deletedPage), undefined, "deleted wishlist row should disappear from page");

console.log(`Wishlist API smoke passed: mode=${mode}, base=${baseUrl}, sku=${smokeItem.skuId}`);
