package cn.iocoder.yudao.module.member.service.trade;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationReviewReqVO;
import cn.iocoder.yudao.module.member.controller.app.trade.vo.AppTradeApplicationSubmitReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.trade.MemberTradeApplicationDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.trade.MemberTradeApplicationMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.trade.MemberTradeApplicationStatusEnum;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.TRADE_APPLICATION_PENDING_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MemberTradeApplicationServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private MemberTradeApplicationServiceImpl tradeApplicationService;

    @Mock
    private MemberTradeApplicationMapper tradeApplicationMapper;
    @Mock
    private MemberUserService memberUserService;
    @Mock
    private MemberUserMapper memberUserMapper;

    @Test
    public void testSubmitTradeApplication_success() {
        AppTradeApplicationSubmitReqVO reqVO = AppTradeApplicationSubmitReqVO.builder()
                .businessName("Studio Ada")
                .country("United States")
                .street("1 Design Way")
                .city("San Francisco")
                .state("California")
                .postalCode("94111")
                .businessDescription("Interior Designer")
                .primaryEmail("DESIGNER@EXAMPLE.COM")
                .authorizedUsers(Collections.singletonList(AppTradeApplicationSubmitReqVO.AuthorizedUser.builder()
                        .firstName("Ada").lastName("Lovelace").email("designer@example.com").build()))
                .businessDocuments(Collections.singletonList(AppTradeApplicationSubmitReqVO.Attachment.builder()
                        .name("license.pdf").url("https://cdn.example/license.pdf").build()))
                .taxDocuments(Collections.emptyList())
                .build();

        MemberTradeApplicationDO result = tradeApplicationService.submitTradeApplication(reqVO);

        assertEquals(MemberTradeApplicationStatusEnum.PENDING.getStatus(), result.getStatus());
        assertEquals("designer@example.com", result.getPrimaryEmail());
        verify(tradeApplicationMapper).insert(org.mockito.ArgumentMatchers.<MemberTradeApplicationDO>argThat(application -> {
            assertEquals("Studio Ada", application.getBusinessName());
            assertEquals("designer@example.com", application.getPrimaryEmail());
            assertEquals(MemberTradeApplicationStatusEnum.PENDING.getStatus(), application.getStatus());
            return application.getAuthorizedUsersJson().contains("Ada")
                    && application.getBusinessDocumentsJson().contains("license.pdf");
        }));
    }

    @Test
    public void testSubmitTradeApplication_whenPendingExists() {
        when(tradeApplicationMapper.selectPendingByPrimaryEmail(eq("designer@example.com")))
                .thenReturn(new MemberTradeApplicationDO().setId(9L));

        AppTradeApplicationSubmitReqVO reqVO = AppTradeApplicationSubmitReqVO.builder()
                .businessName("Studio Ada")
                .country("United States")
                .street("1 Design Way")
                .city("San Francisco")
                .state("California")
                .postalCode("94111")
                .businessDescription("Interior Designer")
                .primaryEmail("designer@example.com")
                .authorizedUsers(Collections.singletonList(AppTradeApplicationSubmitReqVO.AuthorizedUser.builder()
                        .firstName("Ada").lastName("Lovelace").email("designer@example.com").build()))
                .businessDocuments(Collections.singletonList(AppTradeApplicationSubmitReqVO.Attachment.builder()
                        .name("license.pdf").url("https://cdn.example/license.pdf").build()))
                .taxDocuments(Collections.emptyList())
                .build();

        assertServiceException(() -> tradeApplicationService.submitTradeApplication(reqVO),
                TRADE_APPLICATION_PENDING_EXISTS);
    }

    @Test
    public void testApproveTradeApplication_success() {
        MemberTradeApplicationDO application = new MemberTradeApplicationDO()
                .setId(88L)
                .setPrimaryEmail("designer@example.com")
                .setStatus(MemberTradeApplicationStatusEnum.PENDING.getStatus());
        when(tradeApplicationMapper.selectById(eq(88L))).thenReturn(application);
        when(memberUserService.getUserByEmail(eq("designer@example.com")))
                .thenReturn(new MemberUserDO().setId(18L).setEmail("designer@example.com"));

        tradeApplicationService.approveTradeApplication(new MemberTradeApplicationReviewReqVO()
                .setId(88L).setTradeId("RH-TRADE-10086").setReviewReason("Approved"));

        verify(memberUserMapper).updateById(org.mockito.ArgumentMatchers.<MemberUserDO>argThat(user ->
                Long.valueOf(18L).equals(user.getId()) && "RH-TRADE-10086".equals(user.getTradeId())));
        verify(tradeApplicationMapper).updateById(org.mockito.ArgumentMatchers.<MemberTradeApplicationDO>argThat(update ->
                Long.valueOf(88L).equals(update.getId())
                        && MemberTradeApplicationStatusEnum.APPROVED.getStatus().equals(update.getStatus())
                        && "RH-TRADE-10086".equals(update.getTradeId())));
    }

    @Test
    public void testRejectTradeApplication_success() {
        when(tradeApplicationMapper.selectById(eq(88L))).thenReturn(new MemberTradeApplicationDO()
                .setId(88L)
                .setStatus(MemberTradeApplicationStatusEnum.PENDING.getStatus()));

        tradeApplicationService.rejectTradeApplication(new MemberTradeApplicationReviewReqVO()
                .setId(88L).setReviewReason("Missing business license"));

        verify(tradeApplicationMapper).updateById(org.mockito.ArgumentMatchers.<MemberTradeApplicationDO>argThat(update ->
                Long.valueOf(88L).equals(update.getId())
                        && MemberTradeApplicationStatusEnum.REJECTED.getStatus().equals(update.getStatus())
                        && "Missing business license".equals(update.getReviewReason())));
    }
}
