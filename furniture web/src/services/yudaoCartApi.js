import { mapCartResponseToItems } from "./yudaoMappers.js";
import { requestYudao } from "./yudaoRequest.js";

export const addCartItem = (skuId, count = 1, options = {}) =>
  requestYudao("/trade/cart/add", {
    ...options,
    method: "POST",
    body: JSON.stringify({
      skuId,
      count,
      ...(options.registryContext?.registryId ? { registryId: options.registryContext.registryId } : {}),
      ...(options.registryContext?.registryItemId ? { registryItemId: options.registryContext.registryItemId } : {}),
    }),
  });

export const updateCartItemCount = (cartId, count, options = {}) =>
  requestYudao("/trade/cart/update-count", {
    ...options,
    method: "PUT",
    body: JSON.stringify({ id: cartId, count }),
  });

export const deleteCartItems = (cartIds, options = {}) =>
  requestYudao(`/trade/cart/delete?ids=${cartIds.join(",")}`, {
    ...options,
    method: "DELETE",
  });

export const getRemoteCartItems = async (options = {}) => {
  const data = await requestYudao("/trade/cart/list", options);
  return mapCartResponseToItems(data);
};
