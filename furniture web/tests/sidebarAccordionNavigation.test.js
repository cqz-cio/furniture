import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const menu = readFileSync(
  new URL(
    "../../yudao电商管理平台前后端/yudao-ui-admin-vue3/src/layout/components/Menu/src/Menu.vue",
    import.meta.url,
  ),
  "utf8",
);
const appStore = readFileSync(
  new URL(
    "../../yudao电商管理平台前后端/yudao-ui-admin-vue3/src/store/modules/app.ts",
    import.meta.url,
  ),
  "utf8",
);
const interfaceDisplay = readFileSync(
  new URL(
    "../../yudao电商管理平台前后端/yudao-ui-admin-vue3/src/layout/components/Setting/src/components/InterfaceDisplay.vue",
    import.meta.url,
  ),
  "utf8",
);

describe("ERP sidebar accordion navigation", () => {
  it("always allows only one submenu to remain expanded", () => {
    expect(menu).toContain("uniqueOpened={true}");
    expect(menu).not.toContain("getUniqueOpened");
  });

  it("does not expose a switch that can disable the accordion rule", () => {
    expect(appStore).not.toContain("uniqueOpened");
    expect(interfaceDisplay).not.toContain("setting.uniqueOpened");
  });
});
