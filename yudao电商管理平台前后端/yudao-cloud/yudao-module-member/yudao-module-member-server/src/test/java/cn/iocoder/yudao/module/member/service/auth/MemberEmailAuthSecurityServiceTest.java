package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.enums.auth.MemberEmailAuthSceneEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_EMAIL_CAPTCHA_INVALID;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_EMAIL_CAPTCHA_REQUIRED;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_EMAIL_CODE_SEND_TOO_FAST;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_EMAIL_CODE_VERIFY_TOO_MANY;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

public class MemberEmailAuthSecurityServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private MemberEmailAuthSecurityService securityService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    public void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
    }

    @Test
    public void testValidateBeforeSend_whenEmailHourlyLimitReached() {
        String email = "member@example.com";
        Integer scene = MemberEmailAuthSceneEnum.GENERAL_CODE.getScene();
        when(valueOperations.get(eq("member:email-auth:send:email:hour:" + scene + ":" + email))).thenReturn("10");

        assertServiceException(() -> securityService.validateBeforeSend(email, scene, null),
                AUTH_EMAIL_CODE_SEND_TOO_FAST);
    }

    @Test
    public void testValidateBeforeSend_whenIpMinuteLimitReached() {
        String email = "member@example.com";
        Integer scene = MemberEmailAuthSceneEnum.GENERAL_CODE.getScene();
        when(valueOperations.get(eq("member:email-auth:send:ip:minute:unknown"))).thenReturn("3");

        assertServiceException(() -> securityService.validateBeforeSend(email, scene, null),
                AUTH_EMAIL_CAPTCHA_REQUIRED);
    }

    @Test
    public void testRecordValidateFailure_whenTooManyFailures() {
        String email = "member@example.com";
        Integer scene = MemberEmailAuthSceneEnum.GENERAL_CODE.getScene();
        when(valueOperations.increment(eq("member:email-auth:validate:fail:" + scene + ":" + email))).thenReturn(10L);

        assertServiceException(() -> securityService.recordValidateFailure(email, scene),
                AUTH_EMAIL_CODE_VERIFY_TOO_MANY);
    }

    @Test
    public void testCreateCaptchaChallenge_returnsImageCaptcha() {
        cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCaptchaChallengeRespVO challenge =
                securityService.createCaptchaChallenge();

        org.junit.jupiter.api.Assertions.assertNotNull(challenge.getChallengeId());
        org.junit.jupiter.api.Assertions.assertTrue(challenge.getImageBase64().startsWith("data:image/"));
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Arrays.asList("LINE", "CIRCLE", "SHEAR", "MATH")
                .contains(challenge.getCaptchaType()));
        if ("MATH".equals(challenge.getCaptchaType())) {
            org.junit.jupiter.api.Assertions.assertTrue(challenge.getInstruction().contains("计算结果"));
        } else {
            org.junit.jupiter.api.Assertions.assertTrue(challenge.getInstruction().contains("验证码"));
        }
        org.mockito.Mockito.verify(valueOperations).set(eq("member:email-auth:captcha:challenge:" + challenge.getChallengeId()),
                anyString(), org.mockito.ArgumentMatchers.anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    public void testVerifyCaptchaChallenge_whenCodeInvalid() {
        String challengeId = "captcha-1";
        when(valueOperations.get(eq("member:email-auth:captcha:challenge:" + challengeId))).thenReturn("LINE:abcd");

        cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCaptchaVerifyReqVO reqVO =
                new cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCaptchaVerifyReqVO();
        reqVO.setChallengeId(challengeId);
        reqVO.setCode("wrong");

        assertServiceException(() -> securityService.verifyCaptchaChallenge(reqVO), AUTH_EMAIL_CAPTCHA_INVALID);
    }

}
