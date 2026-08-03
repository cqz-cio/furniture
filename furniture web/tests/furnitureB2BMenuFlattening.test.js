import { afterEach, describe, expect, it, vi } from "vitest";

import {
  filterFurnitureLiteMenus,
} from "../../yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/furnitureLite.ts";

const createMallRoutes = () => [
  {
    path: "/mall",
    name: "商城系统",
    parentId: 0,
    children: [
      {
        path: "home",
        name: "商城首页",
        parentId: 2362,
      },
      {
        path: "product",
        name: "商品中心",
        parentId: 2362,
        children: [
          {
            path: "spu",
            name: "商品列表",
            parentId: 2000,
          },
          {
            path: "category",
            name: "商品分类",
            parentId: 2000,
          },
        ],
      },
      {
        path: "trade",
        name: "交易中心",
        parentId: 2362,
      },
    ],
  },
];

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("B2B product-only mall navigation", () => {
  it("removes the product-center middle level without changing page URLs", () => {
    vi.stubEnv("VITE_ADMIN_MODE", "furniture-lite");

    const routes = filterFurnitureLiteMenus(createMallRoutes(), [
      "/mall/product",
      "/mall/product/spu",
      "/mall/product/category",
    ]);

    expect(routes).toHaveLength(1);
    expect(routes[0].name).toBe("商城系统");
    expect(routes[0].children.map((route) => route.name)).toEqual([
      "商品列表",
      "商品分类",
    ]);
    expect(routes[0].children.map((route) => route.path)).toEqual([
      "product/spu",
      "product/category",
    ]);
  });

  it("keeps the normal B2C mall hierarchy unchanged", () => {
    vi.stubEnv("VITE_ADMIN_MODE", "furniture-lite");

    const routes = filterFurnitureLiteMenus(createMallRoutes(), [
      "/mall/home",
      "/mall/product",
      "/mall/product/spu",
      "/mall/product/category",
      "/mall/trade",
    ]);

    expect(routes[0].children.map((route) => route.name)).toEqual([
      "商城首页",
      "商品中心",
      "交易中心",
    ]);
    expect(routes[0].children[1].children.map((route) => route.path)).toEqual([
      "spu",
      "category",
    ]);
  });
});
