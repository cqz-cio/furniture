package cn.iocoder.yudao.module.member.service.trade;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationReviewReqVO;
import cn.iocoder.yudao.module.member.controller.app.trade.vo.AppTradeApplicationSubmitReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.trade.MemberTradeApplicationDO;

import jakarta.validation.Valid;

public interface MemberTradeApplicationService {

    MemberTradeApplicationDO submitTradeApplication(@Valid AppTradeApplicationSubmitReqVO reqVO);

    PageResult<MemberTradeApplicationDO> getTradeApplicationPage(MemberTradeApplicationPageReqVO pageReqVO);

    MemberTradeApplicationDO getTradeApplication(Long id);

    void approveTradeApplication(@Valid MemberTradeApplicationReviewReqVO reqVO);

    void rejectTradeApplication(@Valid MemberTradeApplicationReviewReqVO reqVO);

}
