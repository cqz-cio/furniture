package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.CarrierDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LogisticsProviderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;

import java.util.List;

record LegacyMigrationEvaluation(MigrationOrderResult result, TradeOrderDO order,
                                 List<TradeOrderItemDO> items, CarrierDO carrier,
                                 LegacyMigrationFacts facts, LogisticsProviderDO provider,
                                 String trackingNumber) {

    boolean eligible() {
        return result.outcome() == MigrationOutcome.WOULD_MIGRATE;
    }

}
