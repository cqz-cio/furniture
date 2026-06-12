import { mapSpuToProduct } from "./yudaoMappers.js";
import { requestYudao } from "./yudaoRequest.js";

export const getProductPage = async (params = {}, options = {}) => {
  const search = new URLSearchParams({
    pageNo: "1",
    pageSize: "24",
    ...Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== "")),
  });
  const data = await requestYudao(`/product/spu/page?${search}`, options);
  return {
    list: (data.list || []).map(mapSpuToProduct),
    total: Number(data.total || 0),
  };
};

export const getProductDetail = async (id, options = {}) => {
  const data = await requestYudao(`/product/spu/get-detail?id=${encodeURIComponent(id)}`, options);
  return mapSpuToProduct(data);
};
