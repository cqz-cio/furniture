package cn.iocoder.yudao.module.member.dal.dataobject.giftregistry;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("member_gift_registry_item")
@KeySequence("member_gift_registry_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberGiftRegistryItemDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long registryId;
    private Long userId;
    private Long spuId;
    private Long skuId;
    private String productName;
    private String picUrl;
    private Integer price;
    private Integer quantityRequested;
    private Integer quantityPurchased;
    private String priority;
    private String note;

}
