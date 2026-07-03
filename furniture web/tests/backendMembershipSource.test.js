import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const backendRoot =
  "D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-module-member/yudao-module-member-server/src/main/java/cn/iocoder/yudao/module/member";
const readSource = (path) => readFileSync(path, "utf8").replace(/\r\n/g, "\n");

describe("backend membership account activation boundary", () => {
  it("keeps direct membership opening in Admin and removes the App open endpoint", () => {
    const appController = readSource(`${backendRoot}/controller/app/membership/AppMemberMembershipController.java`);
    const adminController = readSource(`${backendRoot}/controller/admin/membership/MemberMembershipController.java`);

    expect(appController).not.toContain('@PostMapping("/open")');
    expect(appController).toContain('@PostMapping("/checkout-intent")');
    expect(adminController).toContain('@PostMapping("/open")');
    expect(adminController).toContain("member:membership:update");
  });
});
