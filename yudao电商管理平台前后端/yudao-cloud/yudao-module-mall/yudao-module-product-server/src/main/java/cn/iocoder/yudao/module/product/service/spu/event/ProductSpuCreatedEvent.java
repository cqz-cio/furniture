package cn.iocoder.yudao.module.product.service.spu.event;

/**
 * Web 商品及其 SKU 已在本地事务中创建完成。
 */
public record ProductSpuCreatedEvent(Long spuId) {
}
