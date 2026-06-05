package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCodeSendReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailLoginReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCodeValidateReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailRegisterReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailSecureLinkReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthTradeLoginReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.enums.auth.MemberEmailAuthSceneEnum;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.USER_EMAIL_USED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MemberAuthServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private MemberAuthServiceImpl authService;

    @Mock
    private MemberUserService userService;
    @Mock
    private MemberEmailAuthService memberEmailAuthService;
    @Mock
    private SmsCodeApi smsCodeApi;
    @Mock
    private LoginLogApi loginLogApi;
    @Mock
    private SocialUserApi socialUserApi;
    @Mock
    private SocialClientApi socialClientApi;
    @Mock
    private OAuth2TokenCommonApi oauth2TokenApi;

    @Test
    public void testEmailLogin_success() {
        String email = "designer@example.com";
        String password = "admin123";
        MemberUserDO user = new MemberUserDO().setId(10L).setEmail(email)
                .setPassword("encoded").setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(userService.getUserByEmail(eq(email))).thenReturn(user);
        when(userService.isPasswordMatch(eq(password), eq("encoded"))).thenReturn(true);
        when(loginLogApi.createLoginLog(argThat(log -> {
            assertEquals(user.getId(), log.getUserId());
            assertEquals(email, log.getUsername());
            assertEquals(LoginLogTypeEnum.LOGIN_USERNAME.getType(), log.getLogType());
            assertEquals(LoginResultEnum.SUCCESS.getResult(), log.getResult());
            return true;
        }))).thenReturn(success(null));
        when(oauth2TokenApi.createAccessToken(argThat(reqDTO -> {
            assertEquals(user.getId(), reqDTO.getUserId());
            assertEquals(UserTypeEnum.MEMBER.getValue(), reqDTO.getUserType());
            return true;
        }))).thenReturn(success(buildToken(user.getId())));

        AppAuthLoginRespVO respVO = authService.emailLogin(new AppAuthEmailLoginReqVO(email, password));

        assertEquals(user.getId(), respVO.getUserId());
        assertEquals("access-token", respVO.getAccessToken());
        verify(userService).updateUserLogin(eq(user.getId()), eq(null));
    }

    @Test
    public void testEmailRegister_success() {
        String email = "new-designer@example.com";
        String password = "admin123";
        String code = "123456";
        AppAuthEmailRegisterReqVO reqVO = new AppAuthEmailRegisterReqVO(
                "Black", "Furniture", email, password, code, true, true);
        MemberUserDO user = new MemberUserDO().setId(11L).setEmail(email)
                .setPassword("encoded").setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(userService.createUserByEmail(eq(email), eq(password), eq("Black Furniture"),
                eq(null), eq(0))).thenReturn(user);
        when(loginLogApi.createLoginLog(argThat(log -> {
            assertEquals(user.getId(), log.getUserId());
            assertEquals(email, log.getUsername());
            assertEquals(LoginLogTypeEnum.LOGIN_USERNAME.getType(), log.getLogType());
            assertEquals(LoginResultEnum.SUCCESS.getResult(), log.getResult());
            return true;
        }))).thenReturn(success(null));
        when(oauth2TokenApi.createAccessToken(argThat(reqDTO -> reqDTO.getUserId().equals(user.getId()))))
                .thenReturn(success(buildToken(user.getId())));

        AppAuthLoginRespVO respVO = authService.emailRegister(reqVO);

        assertEquals(user.getId(), respVO.getUserId());
        verify(memberEmailAuthService).validateCode(eq(null), argThat(validateReq -> {
            assertEquals(MemberEmailAuthSceneEnum.GENERAL_CODE.getScene(), validateReq.getScene());
            assertEquals(email, validateReq.getEmail());
            assertEquals(code, validateReq.getCode());
            return true;
        }));
        verify(userService).createUserByEmail(eq(email), eq(password), eq("Black Furniture"),
                eq(null), eq(0));
        verify(memberEmailAuthService, never()).sendVerifyEmail(eq(user.getId()), eq(email));
    }

    @Test
    public void testSendEmailSecureLink() {
        String email = "secure@example.com";

        authService.sendEmailSecureLink(new AppAuthEmailSecureLinkReqVO(email));

        verify(memberEmailAuthService).sendLoginLink(eq(email));
        verify(userService, never()).getUserByMobile(eq(email));
    }

    @Test
    public void testSendEmailCode_registerWhenEmailExists() {
        String email = "member@example.com";
        when(userService.getUserByEmail(eq(email))).thenReturn(new MemberUserDO().setId(20L).setEmail(email));

        AppAuthEmailCodeSendReqVO reqVO = new AppAuthEmailCodeSendReqVO(
                MemberEmailAuthSceneEnum.GENERAL_CODE.getScene(), email);

        assertServiceException(() -> authService.sendEmailCode(null, reqVO), USER_EMAIL_USED);
        verify(memberEmailAuthService, never()).sendCode(eq(null), eq(reqVO));
    }

    @Test
    public void testTradeLogin_success() {
        String tradeId = "RH-TRADE-10086";
        String email = "trade@example.com";
        MemberUserDO user = new MemberUserDO().setId(12L).setEmail(email)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setTradeId(tradeId);
        when(userService.getUserByEmail(eq(email))).thenReturn(user);
        when(loginLogApi.createLoginLog(argThat(log -> {
            assertEquals(user.getId(), log.getUserId());
            assertEquals(email, log.getUsername());
            assertEquals(LoginLogTypeEnum.LOGIN_USERNAME.getType(), log.getLogType());
            assertEquals(LoginResultEnum.SUCCESS.getResult(), log.getResult());
            return true;
        }))).thenReturn(success(null));
        when(oauth2TokenApi.createAccessToken(argThat(reqDTO -> reqDTO.getUserId().equals(user.getId()))))
                .thenReturn(success(buildToken(user.getId())));

        AppAuthLoginRespVO respVO = authService.tradeLogin(new AppAuthTradeLoginReqVO(tradeId, email));

        assertEquals(user.getId(), respVO.getUserId());
    }

    private static OAuth2AccessTokenRespDTO buildToken(Long userId) {
        OAuth2AccessTokenRespDTO token = new OAuth2AccessTokenRespDTO();
        token.setUserId(userId);
        token.setUserType(UserTypeEnum.MEMBER.getValue());
        token.setAccessToken("access-token");
        token.setRefreshToken("refresh-token");
        token.setExpiresTime(LocalDateTime.now().plusDays(1));
        return token;
    }

}
