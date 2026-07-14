import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const pagePath = (fileName) => new URL(`../src/pages/${fileName}`, import.meta.url);
const readPageSource = (fileName) => readFileSync(pagePath(fileName), "utf8").replace(/\r\n/g, "\n");

const expectLocalizedVisibleCopy = (fileName, { requiredKeys = [], forbiddenSnippets = [] }) => {
  const source = readPageSource(fileName);

  for (const key of requiredKeys) {
    expect(
      source.includes(`t("${key}")`) || source.includes(`t('${key}')`),
      `${fileName} should reference ${key}`,
    ).toBe(true);
  }

  for (const snippet of forbiddenSnippets) {
    expect(source, `${fileName} should not hard-code visible copy: ${snippet}`).not.toContain(snippet);
  }
};

describe("gift registry pages and routes", () => {
  it("adds create, find and manage registry pages", () => {
    ["GiftRegistryCreatePage.vue", "GiftRegistryFindPage.vue", "GiftRegistryManagePage.vue"].forEach((fileName) => {
      expect(existsSync(pagePath(fileName)), `${fileName} should exist`).toBe(true);
    });
  });

  it("registers gift registry routes in App.vue", () => {
    const app = readFileSync(new URL("../src/App.vue", import.meta.url), "utf8");

    expect(app).toContain('"gift-registry-create": "/gift-registry/create"');
    expect(app).toContain('"gift-registry-find": "/gift-registry/find"');
    expect(app).toContain('"gift-registry-manage": "/gift-registry/manage"');
  });

  it("uses the gift registry model from the create page", () => {
    const source = readPageSource("GiftRegistryCreatePage.vue");

    expect(source).toContain("createGiftRegistryDraft");
    expect(source).toContain("getGiftRegistrySteps");
    expect(source).toContain("getRegistryShareState");
    expect(source).toContain("createYudaoGiftRegistry");
    expect(source).toContain("readYudaoToken");
  });

  it("keeps localized event type placeholder out of registry draft defaults", () => {
    const create = readPageSource("GiftRegistryCreatePage.vue");
    const manage = readPageSource("GiftRegistryManagePage.vue");

    expect(create).not.toContain('createGiftRegistryDraft({\n    event: { type: t("giftRegistry.create.fields.eventTypePlaceholder") }');
    expect(manage).not.toContain('createGiftRegistryDraft({ event: { type: t("giftRegistry.create.fields.eventTypePlaceholder") } })');
    expect(manage).not.toContain('createGiftRegistryDraft({ event: { type: t("giftRegistry.create.fields.eventTypePlaceholder") } });');
    expect(create).toContain(':placeholder="t(\'giftRegistry.create.fields.eventTypePlaceholder\')"');
  });

  it("loads registry pages from persistent Yudao APIs instead of fixed demo data", () => {
    const manage = readPageSource("GiftRegistryManagePage.vue");
    const find = readPageSource("GiftRegistryFindPage.vue");
    const publicPage = readPageSource("GiftRegistryPage.vue");
    const create = readPageSource("GiftRegistryCreatePage.vue");

    expect(manage).toContain("getMyYudaoGiftRegistry");
    expect(manage).toContain("addYudaoGiftRegistryItem");
    expect(manage).toContain("registryLoadState");
    expect(manage).not.toContain('id: "registry-stone-2026"');
    expect(manage).toContain('t("giftRegistry.manage.title")');
    expect(find).toContain("searchPublicYudaoGiftRegistries");
    expect(find).not.toContain("sampleRegistries");
    expect(find).toContain('t("giftRegistry.find.title")');
    expect(create).toContain('t("giftRegistry.create.title")');
    expect(publicPage).toContain("getPublicYudaoGiftRegistry");
    expect(publicPage).toContain("publicCode");
    expect(publicPage).toContain('t("giftRegistry.eyebrow")');
  });

  it("imports registry visibility in the manage page before using it", () => {
    const manage = readPageSource("GiftRegistryManagePage.vue");

    expect(manage).toContain("REGISTRY_VISIBILITY");
    expect(manage).toMatch(/import\s*\{[\s\S]*REGISTRY_VISIBILITY[\s\S]*\}\s*from\s*\"..\/services\/giftRegistry\.js\";/);
  });

  it("guards public registry page against visible English regressions", () => {
    expectLocalizedVisibleCopy("GiftRegistryPage.vue", {
      requiredKeys: [
        "giftRegistry.eyebrow",
        "giftRegistry.home.title",
        "giftRegistry.home.description",
        "giftRegistry.public.titleFallback",
        "giftRegistry.public.eventFallback",
        "giftRegistry.public.unavailable",
        "giftRegistry.public.viewProduct",
        "giftRegistry.public.addGiftToBag",
        "giftRegistry.public.noGiftsTitle",
        "giftRegistry.public.noGiftsDescription",
      ],
      forbiddenSnippets: [
        ">Gift Registry<",
        ">View Product<",
        ">Add Gift To Bag<",
        "No Gifts Yet",
        "Check back after the owner adds items.",
      ],
    });
  });

  it("guards find registry page against visible English regressions", () => {
    expectLocalizedVisibleCopy("GiftRegistryFindPage.vue", {
      requiredKeys: [
        "giftRegistry.eyebrow",
        "giftRegistry.find.title",
        "giftRegistry.find.description",
        "giftRegistry.find.create",
        "giftRegistry.find.manage",
        "giftRegistry.find.fields.keywordLabel",
        "giftRegistry.find.fields.keywordPlaceholder",
        "giftRegistry.find.fields.eventMonthLabel",
        "giftRegistry.find.fields.eventMonthPlaceholder",
        "giftRegistry.find.search",
        "giftRegistry.find.empty",
        "giftRegistry.find.unavailable",
      ],
      forbiddenSnippets: [
        ">Find a Registry<",
        ">Create a Registry<",
        ">Manage Your Registry<",
        ">Search<",
      ],
    });
  });

  it("guards create registry page against visible English regressions", () => {
    expectLocalizedVisibleCopy("GiftRegistryCreatePage.vue", {
      requiredKeys: [
        "giftRegistry.create.title",
        "giftRegistry.create.description",
        "giftRegistry.create.flow",
        "giftRegistry.common.complete",
        "giftRegistry.common.open",
        "giftRegistry.create.fields.eventTypeLabel",
        "giftRegistry.create.fields.eventTypePlaceholder",
        "giftRegistry.create.fields.emailLabel",
        "giftRegistry.create.fields.emailPlaceholder",
        "giftRegistry.create.sections.privacy",
        "giftRegistry.create.share.ready",
        "giftRegistry.create.share.completeRequired",
        "giftRegistry.create.share.publicPage",
        "giftRegistry.create.actions.create",
        "giftRegistry.create.actions.saving",
        "giftRegistry.create.messages.signInRequired",
        "giftRegistry.create.messages.saved",
        "giftRegistry.create.messages.error",
      ],
      forbiddenSnippets: [
        ">Create a Registry<",
        ">Create Flow<",
        ">Registry Visibility<",
        "Ready to share",
        "Complete required sections",
      ],
    });
  });

  it("guards manage registry page against visible English regressions", () => {
    expectLocalizedVisibleCopy("GiftRegistryManagePage.vue", {
      requiredKeys: [
        "giftRegistry.manage.eyebrow",
        "giftRegistry.manage.title",
        "giftRegistry.manage.description",
        "giftRegistry.manage.signInTitle",
        "giftRegistry.manage.signIn",
        "giftRegistry.manage.addProductTitle",
        "giftRegistry.manage.addGift",
        "giftRegistry.manage.viewPublic",
        "giftRegistry.manage.viewProduct",
        "giftRegistry.manage.messages.empty",
        "giftRegistry.manage.messages.loadError",
        "giftRegistry.manage.messages.createBeforeAdd",
        "giftRegistry.manage.messages.itemSaved",
        "giftRegistry.manage.messages.itemError",
        "giftRegistry.manage.actions.visibility.title",
        "giftRegistry.manage.actions.visibility.cta",
        "giftRegistry.manage.actions.items.title",
        "giftRegistry.manage.actions.items.cta",
      ],
      forbiddenSnippets: [
        ">Manage Your Registry<",
        ">Sign In Required<",
        ">Add Gift<",
        ">View Registry<",
        ">View Product<",
      ],
    });
  });

  it("lets PDP add the current real product to the signed-in user's gift registry", () => {
    const source = readPageSource("SofaPdpPage.vue");

    expect(source).toContain("getMyYudaoGiftRegistry");
    expect(source).toContain("addYudaoGiftRegistryItem");
    expect(source).toContain("registryProductToItemPayload");
    expect(source).toContain("handleAddToRegistry");
    expect(source).toContain('source.value !== "yudao"');
    expect(source).toContain("readYudaoToken()");
    expect(source).toContain('class="product-registry-button"');
  });

  it("lets public registry gift items enter the cart with registry context", () => {
    const source = readPageSource("GiftRegistryPage.vue");

    expect(source).toContain('defineEmits(["add-to-cart"])');
    expect(source).toContain("registryItemToCartProduct");
    expect(source).toContain("handleAddRegistryGiftToCart");
    expect(source).toContain("registryContext");
    expect(source).toContain('class="registry-cart-button"');
    expect(source).toContain(":href=\"`/product?id=${item.spuId}&registryItemId=${item.id}`\"");
  });

  it("keeps production registry failures explicit instead of silently using demo data", () => {
    const service = readFileSync(new URL("../src/services/giftRegistry.js", import.meta.url), "utf8");
    const manage = readPageSource("GiftRegistryManagePage.vue");
    const find = readPageSource("GiftRegistryFindPage.vue");

    expect(service).toContain("canUseGiftRegistryDemoFallback");
    expect(manage).toContain("import.meta.env.PROD");
    expect(find).toContain("import.meta.env.PROD");
  });
});
