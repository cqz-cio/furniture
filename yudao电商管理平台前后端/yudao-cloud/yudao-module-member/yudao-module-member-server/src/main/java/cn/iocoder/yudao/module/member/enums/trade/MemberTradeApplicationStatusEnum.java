package cn.iocoder.yudao.module.member.enums.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MemberTradeApplicationStatusEnum {

    PENDING(0),
    APPROVED(1),
    REJECTED(2);

    private final Integer status;

}
