package cn.iocoder.yudao.module.member.service.trade;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationReviewReqVO;
import cn.iocoder.yudao.module.member.controller.app.trade.vo.AppTradeApplicationSubmitReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.trade.MemberTradeApplicationDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.trade.MemberTradeApplicationMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.trade.MemberTradeApplicationStatusEnum;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

@Service
public class MemberTradeApplicationServiceImpl implements MemberTradeApplicationService {

    @Resource
    private MemberTradeApplicationMapper tradeApplicationMapper;
    @Resource
    private MemberUserService memberUserService;
    @Resource
    private MemberUserMapper memberUserMapper;

    @Override
    @Transactional
    public MemberTradeApplicationDO submitTradeApplication(AppTradeApplicationSubmitReqVO reqVO) {
        String primaryEmail = normalizeEmail(reqVO.getPrimaryEmail());
        if (tradeApplicationMapper.selectPendingByPrimaryEmail(primaryEmail) != null) {
            throw exception(TRADE_APPLICATION_PENDING_EXISTS);
        }

        MemberTradeApplicationDO application = MemberTradeApplicationDO.builder()
                .businessName(StrUtil.trim(reqVO.getBusinessName()))
                .country(StrUtil.trim(reqVO.getCountry()))
                .street(StrUtil.trim(reqVO.getStreet()))
                .address2(StrUtil.trim(reqVO.getAddress2()))
                .city(StrUtil.trim(reqVO.getCity()))
                .state(StrUtil.trim(reqVO.getState()))
                .postalCode(StrUtil.trim(reqVO.getPostalCode()))
                .businessDescription(StrUtil.trim(reqVO.getBusinessDescription()))
                .website(StrUtil.trim(reqVO.getWebsite()))
                .portfolio(StrUtil.trim(reqVO.getPortfolio()))
                .instagram(StrUtil.trim(reqVO.getInstagram()))
                .pinterest(StrUtil.trim(reqVO.getPinterest()))
                .houzz(StrUtil.trim(reqVO.getHouzz()))
                .linkedin(StrUtil.trim(reqVO.getLinkedin()))
                .primaryEmail(primaryEmail)
                .authorizedUsersJson(JsonUtils.toJsonString(reqVO.getAuthorizedUsers()))
                .businessDocumentsJson(JsonUtils.toJsonString(reqVO.getBusinessDocuments()))
                .taxDocumentsJson(JsonUtils.toJsonString(reqVO.getTaxDocuments()))
                .emailOptIn(reqVO.getEmailOptIn())
                .status(MemberTradeApplicationStatusEnum.PENDING.getStatus())
                .build();
        tradeApplicationMapper.insert(application);
        return application;
    }

    @Override
    public PageResult<MemberTradeApplicationDO> getTradeApplicationPage(MemberTradeApplicationPageReqVO pageReqVO) {
        return tradeApplicationMapper.selectPage(pageReqVO);
    }

    @Override
    public MemberTradeApplicationDO getTradeApplication(Long id) {
        return tradeApplicationMapper.selectById(id);
    }

    @Override
    @Transactional
    public void approveTradeApplication(MemberTradeApplicationReviewReqVO reqVO) {
        MemberTradeApplicationDO application = validatePendingApplication(reqVO.getId());
        String tradeId = StrUtil.trim(reqVO.getTradeId());
        if (StrUtil.isBlank(tradeId)) {
            throw exception(TRADE_APPLICATION_NOT_EXISTS);
        }
        MemberUserDO user = memberUserService.getUserByEmail(application.getPrimaryEmail());
        if (user == null) {
            throw exception(TRADE_APPLICATION_USER_NOT_EXISTS);
        }
        memberUserMapper.updateById(new MemberUserDO().setId(user.getId()).setTradeId(tradeId));
        tradeApplicationMapper.updateById(new MemberTradeApplicationDO()
                .setId(application.getId())
                .setStatus(MemberTradeApplicationStatusEnum.APPROVED.getStatus())
                .setTradeId(tradeId)
                .setReviewReason(reqVO.getReviewReason())
                .setReviewTime(LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void rejectTradeApplication(MemberTradeApplicationReviewReqVO reqVO) {
        MemberTradeApplicationDO application = validatePendingApplication(reqVO.getId());
        tradeApplicationMapper.updateById(new MemberTradeApplicationDO()
                .setId(application.getId())
                .setStatus(MemberTradeApplicationStatusEnum.REJECTED.getStatus())
                .setReviewReason(reqVO.getReviewReason())
                .setReviewTime(LocalDateTime.now()));
    }

    private MemberTradeApplicationDO validatePendingApplication(Long id) {
        MemberTradeApplicationDO application = tradeApplicationMapper.selectById(id);
        if (application == null) {
            throw exception(TRADE_APPLICATION_NOT_EXISTS);
        }
        if (!MemberTradeApplicationStatusEnum.PENDING.getStatus().equals(application.getStatus())) {
            throw exception(TRADE_APPLICATION_REVIEWED);
        }
        return application;
    }

    private String normalizeEmail(String email) {
        return StrUtil.isBlank(email) ? email : StrUtil.trim(email).toLowerCase();
    }

}
