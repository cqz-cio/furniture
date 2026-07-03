package cn.iocoder.yudao.module.member.controller.admin.giftregistry.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MemberGiftRegistryPageReqVO extends PageParam {

    private Long userId;
    private String registrantName;
    private String eventType;
    private String status;
    private String publicCode;

}
