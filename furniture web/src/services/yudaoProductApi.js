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

export const getAllProducts = async (params = {}, options = {}) => {
  const requestedPageSize = Number(params.pageSize || 100);
  const pageSize = Math.min(100, Math.max(1, Number.isFinite(requestedPageSize) ? requestedPageSize : 100));
  const baseParams = { ...params };
  delete baseParams.pageNo;
  delete baseParams.pageSize;

  const list = [];
  let pageNo = 1;
  let total = 0;
  do {
    const page = await getProductPage({ ...baseParams, pageNo, pageSize }, options);
    list.push(...page.list);
    total = page.total;
    if (page.list.length === 0) break;
    pageNo += 1;
  } while (list.length < total);

  return { list, total };
};

export const getProductDetail = async (id, options = {}) => {
  const data = await requestYudao(`/product/spu/get-detail?id=${encodeURIComponent(id)}`, options);
  return mapSpuToProduct(data);
};
