import { describe, expect, it } from "vitest";
import {
  getProductPage as getProductPageFromFacade,
  getRemoteWishlistItems as getRemoteWishlistItemsFromFacade,
  loginByEmailPassword as loginByEmailPasswordFromFacade,
  mapOrderDetail as mapOrderDetailFromFacade,
  readYudaoToken as readYudaoTokenFromFacade,
  settleOrder as settleOrderFromFacade,
  updateFavoriteCount as updateFavoriteCountFromFacade,
} from "../src/services/yudaoClient.js";
import { loginByEmailPassword } from "../src/services/yudaoAuthApi.js";
import { mapOrderDetail } from "../src/services/yudaoMappers.js";
import { settleOrder } from "../src/services/yudaoOrderApi.js";
import { getProductPage } from "../src/services/yudaoProductApi.js";
import { getRemoteWishlistItems, updateFavoriteCount } from "../src/services/yudaoFavoriteApi.js";
import { readYudaoToken } from "../src/services/yudaoRequest.js";

describe("Yudao client facade", () => {
  it("re-exports the domain API surface for backwards compatibility", () => {
    expect(getProductPageFromFacade).toBe(getProductPage);
    expect(getRemoteWishlistItemsFromFacade).toBe(getRemoteWishlistItems);
    expect(updateFavoriteCountFromFacade).toBe(updateFavoriteCount);
    expect(loginByEmailPasswordFromFacade).toBe(loginByEmailPassword);
    expect(mapOrderDetailFromFacade).toBe(mapOrderDetail);
    expect(readYudaoTokenFromFacade).toBe(readYudaoToken);
    expect(settleOrderFromFacade).toBe(settleOrder);
  });
});
