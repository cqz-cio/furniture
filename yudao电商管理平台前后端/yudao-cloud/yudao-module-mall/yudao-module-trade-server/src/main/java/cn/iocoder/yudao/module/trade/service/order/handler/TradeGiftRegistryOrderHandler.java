package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.module.member.api.giftregistry.MemberGiftRegistryApi;
import cn.iocoder.yudao.module.member.api.giftregistry.dto.MemberGiftRegistryPurchaseRecordReqDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class TradeGiftRegistryOrderHandler implements TradeOrderHandler {

    @Resource
    private MemberGiftRegistryApi memberGiftRegistryApi;

    @Override
    public void afterPayOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        List<MemberGiftRegistryPurchaseRecordReqDTO.Item> registryItems = buildRegistryItems(order, orderItems);
        if (registryItems.isEmpty()) {
            return;
        }
        try {
            memberGiftRegistryApi.recordPurchase(new MemberGiftRegistryPurchaseRecordReqDTO().setItems(registryItems))
                    .checkError();
        } catch (Exception e) {
            log.warn("[afterPayOrder][order({}) Gift Registry purchase writeback failed]", order.getId(), e);
        }
    }

    private List<MemberGiftRegistryPurchaseRecordReqDTO.Item> buildRegistryItems(TradeOrderDO order,
                                                                                List<TradeOrderItemDO> orderItems) {
        List<MemberGiftRegistryPurchaseRecordReqDTO.Item> registryItems = new ArrayList<>();
        if (orderItems == null || orderItems.isEmpty()) {
            return registryItems;
        }
        for (TradeOrderItemDO orderItem : orderItems) {
            if (orderItem.getRegistryId() == null || orderItem.getRegistryItemId() == null) {
                continue;
            }
            registryItems.add(new MemberGiftRegistryPurchaseRecordReqDTO.Item()
                    .setRegistryId(orderItem.getRegistryId())
                    .setRegistryItemId(orderItem.getRegistryItemId())
                    .setCount(orderItem.getCount())
                    .setOrderId(order.getId())
                    .setOrderItemId(orderItem.getId()));
        }
        return registryItems;
    }

}
