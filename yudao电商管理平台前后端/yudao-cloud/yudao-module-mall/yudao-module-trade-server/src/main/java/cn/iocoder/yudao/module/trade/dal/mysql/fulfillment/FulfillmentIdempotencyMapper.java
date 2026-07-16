package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentIdempotencyDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface FulfillmentIdempotencyMapper extends BaseMapperX<FulfillmentIdempotencyDO> {

    default FulfillmentIdempotencyDO selectByOperationAndKeyHash(Long tenantId, String operation, String keyHash) {
        return selectOne(new LambdaQueryWrapperX<FulfillmentIdempotencyDO>()
                .eq(FulfillmentIdempotencyDO::getTenantId, tenantId)
                .eq(FulfillmentIdempotencyDO::getOperation, operation)
                .eq(FulfillmentIdempotencyDO::getIdempotencyKeyHash, keyHash));
    }

    @Select("SELECT * FROM trade_fulfillment_idempotency WHERE tenant_id = #{tenantId} "
            + "AND operation = #{operation} AND idempotency_key_hash = #{keyHash} "
            + "AND deleted = FALSE FOR UPDATE")
    FulfillmentIdempotencyDO selectByOperationAndKeyHashForUpdate(@Param("tenantId") Long tenantId,
                                                                   @Param("operation") String operation,
                                                                   @Param("keyHash") String keyHash);

    default int completeProcessingById(Long tenantId, Long id, String requestHash,
                                        Long resourceId, LocalDateTime expiresAt) {
        return update(null, new LambdaUpdateWrapper<FulfillmentIdempotencyDO>()
                .eq(FulfillmentIdempotencyDO::getTenantId, tenantId)
                .eq(FulfillmentIdempotencyDO::getId, id)
                .eq(FulfillmentIdempotencyDO::getRequestHash, requestHash)
                .eq(FulfillmentIdempotencyDO::getStatus, "PROCESSING")
                .isNull(FulfillmentIdempotencyDO::getResourceId)
                .set(FulfillmentIdempotencyDO::getResourceId, resourceId)
                .set(FulfillmentIdempotencyDO::getStatus, "COMPLETED")
                .set(FulfillmentIdempotencyDO::getExpiresAt, expiresAt));
    }

}
