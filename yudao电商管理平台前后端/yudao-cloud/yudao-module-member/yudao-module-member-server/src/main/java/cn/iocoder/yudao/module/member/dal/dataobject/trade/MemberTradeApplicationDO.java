package cn.iocoder.yudao.module.member.dal.dataobject.trade;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("member_trade_application")
@KeySequence("member_trade_application_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberTradeApplicationDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String businessName;
    private String country;
    private String street;
    private String address2;
    private String city;
    private String state;
    private String postalCode;
    private String businessDescription;
    private String website;
    private String portfolio;
    private String instagram;
    private String pinterest;
    private String houzz;
    private String linkedin;
    private String primaryEmail;
    private String authorizedUsersJson;
    private String businessDocumentsJson;
    private String taxDocumentsJson;
    private Boolean emailOptIn;
    private Integer status;
    private String tradeId;
    private String reviewReason;
    private LocalDateTime reviewTime;
    private Long reviewerId;

}
