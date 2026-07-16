package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FulfillmentOutboxEventMapper extends BaseMapperX<FulfillmentOutboxEventDO> {
}
