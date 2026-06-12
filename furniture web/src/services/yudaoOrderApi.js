import { mapOrderDetail, mapOrderPage, mapSettlementResponse } from "./yudaoMappers.js";
import { requestYudao } from "./yudaoRequest.js";

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

export const createOrder = async (payload, options = {}) =>
  requestYudao("/trade/order/create", {
    ...options,
    method: "POST",
    body: JSON.stringify(payload),
  });

export const getOrderPage = async (params = {}, options = {}) => {
  const search = new URLSearchParams({ pageNo: "1", pageSize: "10", ...params });
  const data = await requestYudao(`/trade/order/page?${search}`, options);
  return mapOrderPage(data);
};

export const getOrderDetail = async (id, options = {}) => {
  const data = await requestYudao(`/trade/order/get-detail?id=${encodeURIComponent(id)}`, options);
  return data ? mapOrderDetail(data) : null;
};
