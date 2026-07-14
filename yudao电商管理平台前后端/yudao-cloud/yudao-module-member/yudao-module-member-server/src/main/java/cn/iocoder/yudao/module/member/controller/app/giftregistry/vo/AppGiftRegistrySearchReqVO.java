package cn.iocoder.yudao.module.member.controller.app.giftregistry.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppGiftRegistrySearchReqVO extends PageParam {

    private String keyword;
    private String eventMonth;
    private LocalDate eventStart;
    private LocalDate eventEnd;

}
