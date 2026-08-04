import { afterEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import {
  loadWebsiteNavigationPreview,
  parseWebsiteNavigationPreviewHash,
} from "../src/services/yudaoNavigationApi.js";

const ticket = `pv_${"A".repeat(43)}`;
const session = `ps_${"B".repeat(43)}`;

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("website navigation preview", () => {
  it("requires both the one-time ticket and its tenant context", () => {
    expect(parseWebsiteNavigationPreviewHash(`#ticket=${ticket}&tenantId=162`)).toEqual({
      ticket,
      tenantId: "162",
    });
    expect(() => parseWebsiteNavigationPreviewHash(`#ticket=${ticket}`)).toThrow(
      "invalid or incomplete",
    );
  });

  it("exchanges the ticket, loads the draft navigation, and removes the consumed hash", async () => {
    const navigation = {
      siteId: 1,
      locale: "en",
      items: [{ key: "PAGE_HOME", label: "Home", href: "/", children: [] }],
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ code: 0, data: { session, expiresInSeconds: 1800 } }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ code: 0, data: navigation }),
      });
    vi.stubGlobal("fetch", fetchMock);
    const history = { state: { preview: true }, replaceState: vi.fn() };

    await expect(
      loadWebsiteNavigationPreview({
        hash: `#ticket=${ticket}&tenantId=162`,
        location: { pathname: "/preview/navigation", search: "" },
        history,
      }),
    ).resolves.toEqual(navigation);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/seo\/navigation\/preview\/exchange$/);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      method: "POST",
      headers: expect.objectContaining({ "tenant-id": "162" }),
    });
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/seo\/navigation\/preview$/);
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: "GET",
      headers: expect.objectContaining({
        "tenant-id": "162",
        "X-Website-Preview-Session": session,
      }),
    });
    expect(history.replaceState).toHaveBeenCalledWith(
      history.state,
      "",
      "/preview/navigation",
    );
  });

  it("wires the preview result into the real shared header", () => {
    const appSource = readFileSync(new URL("../src/App.vue", import.meta.url), "utf8");
    const headerSource = readFileSync(
      new URL("../src/components/RhHeader.vue", import.meta.url),
      "utf8",
    );
    const productListSource = readFileSync(
      new URL("../src/pages/SofasPlpPage.vue", import.meta.url),
      "utf8",
    );

    expect(appSource).toContain('"navigation-preview": "/preview/navigation"');
    expect(appSource).toContain(":navigation-items=\"navigationPreviewItems\"");
    expect(headerSource).toContain("if (props.navigationPreview) return props.navigationItems;");
    expect(headerSource).toContain("item?.children?.length");
    expect(appSource).toContain('normalizedPath.startsWith("/products/category/")');
    expect(productListSource).toContain("getAllProducts(categoryId ? { categoryId } : {})");
  });
});
