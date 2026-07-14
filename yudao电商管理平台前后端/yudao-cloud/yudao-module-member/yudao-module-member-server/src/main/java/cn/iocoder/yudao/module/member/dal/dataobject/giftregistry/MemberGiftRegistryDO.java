package cn.iocoder.yudao.module.member.dal.dataobject.giftregistry;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

@TableName("member_gift_registry")
@KeySequence("member_gift_registry_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberGiftRegistryDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long userId;
    private String publicCode;
    private String registrantName;
    private String coRegistrantName;
    private String email;
    private String phone;
    private String eventType;
    private LocalDate eventDate;
    private String eventLocation;
    private String visibility;
    private String status;
    private Boolean giftCardPreference;
    private Boolean messagePreference;
    private String beforeEventAddressLine1;
    private String beforeEventAddressLine2;
    private String beforeEventCity;
    private String beforeEventRegion;
    private String beforeEventPostalCode;
    private String beforeEventCountry;
    private String afterEventAddressLine1;
    private String afterEventAddressLine2;
    private String afterEventCity;
    private String afterEventRegion;
    private String afterEventPostalCode;
    private String afterEventCountry;

}
