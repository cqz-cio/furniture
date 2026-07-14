package cn.iocoder.yudao.module.member.service.auth;

import cn.hutool.captcha.AbstractCaptcha;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCaptchaChallengeRespVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCaptchaVerifyReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCaptchaVerifyRespVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_EMAIL_CAPTCHA_INVALID;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_EMAIL_CAPTCHA_REQUIRED;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_EMAIL_CODE_SEND_TOO_FAST;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_EMAIL_CODE_VERIFY_TOO_MANY;

@Service
public class MemberEmailAuthSecurityService {

    private static final String KEY_PREFIX = "member:email-auth:";

    private static final int EMAIL_HOUR_LIMIT = 10;
    private static final int IP_MINUTE_LIMIT = 3;
    private static final int IP_HOUR_LIMIT = 20;
    private static final int IP_DAY_LIMIT = 50;
    private static final int VERIFY_FAIL_LIMIT = 10;

    private static final int CAPTCHA_WIDTH = 160;
    private static final int CAPTCHA_HEIGHT = 48;
    private static final int CAPTCHA_CODE_COUNT = 4;
    private static final int LINE_INTERFERE_COUNT = 30;
    private static final int CIRCLE_INTERFERE_COUNT = 20;
    private static final int SHEAR_INTERFERE_THICKNESS = 4;

    private static final String CAPTCHA_TYPE_LINE = "LINE";
    private static final String CAPTCHA_TYPE_CIRCLE = "CIRCLE";
    private static final String CAPTCHA_TYPE_SHEAR = "SHEAR";
    private static final String CAPTCHA_TYPE_MATH = "MATH";

    private static final Duration MINUTE_TTL = Duration.ofMinutes(1);
    private static final Duration HOUR_TTL = Duration.ofHours(1);
    private static final Duration DAY_TTL = Duration.ofDays(1);
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void validateBeforeSend(String email, Integer scene, String captchaVerification) {
        if (getCount(emailHourKey(email, scene)) >= EMAIL_HOUR_LIMIT) {
            throw exception(AUTH_EMAIL_CODE_SEND_TOO_FAST);
        }
        boolean requireCaptcha = getCount(ipMinuteKey()) >= IP_MINUTE_LIMIT
                || getCount(ipHourKey()) >= IP_HOUR_LIMIT
                || getCount(ipDayKey()) >= IP_DAY_LIMIT
                || getCount(validateFailKey(email, scene)) >= VERIFY_FAIL_LIMIT;
        if (requireCaptcha) {
            consumeCaptchaVerification(captchaVerification);
        }
    }

    public void recordSend(String email, Integer scene) {
        increment(emailHourKey(email, scene), HOUR_TTL);
        increment(ipMinuteKey(), MINUTE_TTL);
        increment(ipHourKey(), HOUR_TTL);
        increment(ipDayKey(), DAY_TTL);
    }

    public void validateBeforeCheckCode(String email, Integer scene) {
        if (getCount(validateFailKey(email, scene)) >= VERIFY_FAIL_LIMIT) {
            throw exception(AUTH_EMAIL_CODE_VERIFY_TOO_MANY);
        }
    }

    public void recordValidateFailure(String email, Integer scene) {
        Long count = stringRedisTemplate.opsForValue().increment(validateFailKey(email, scene));
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(validateFailKey(email, scene), HOUR_TTL);
        }
        if (count != null && count >= VERIFY_FAIL_LIMIT) {
            throw exception(AUTH_EMAIL_CODE_VERIFY_TOO_MANY);
        }
    }

    public void clearValidateFailures(String email, Integer scene) {
        stringRedisTemplate.delete(validateFailKey(email, scene));
    }

    public AppAuthEmailCaptchaChallengeRespVO createCaptchaChallenge() {
        String challengeId = IdUtil.fastSimpleUUID();
        CaptchaImage captchaImage = createRandomCaptchaImage();
        stringRedisTemplate.opsForValue().set(captchaChallengeKey(challengeId),
                captchaImage.type + ":" + captchaImage.captcha.getCode(),
                CAPTCHA_TTL.toMillis(), TimeUnit.MILLISECONDS);
        return new AppAuthEmailCaptchaChallengeRespVO(challengeId, instruction(captchaImage.type),
                captchaImage.captcha.getImageBase64Data(), captchaImage.type);
    }

    public AppAuthEmailCaptchaVerifyRespVO verifyCaptchaChallenge(AppAuthEmailCaptchaVerifyReqVO reqVO) {
        String challengeKey = captchaChallengeKey(reqVO.getChallengeId());
        String expected = stringRedisTemplate.opsForValue().get(challengeKey);
        if (!verifyCaptchaCode(expected, reqVO.getCode())) {
            stringRedisTemplate.delete(challengeKey);
            throw exception(AUTH_EMAIL_CAPTCHA_INVALID);
        }
        stringRedisTemplate.delete(challengeKey);
        String captchaVerification = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(captchaVerificationKey(captchaVerification), "1",
                CAPTCHA_TTL.toMillis(), TimeUnit.MILLISECONDS);
        return new AppAuthEmailCaptchaVerifyRespVO(captchaVerification);
    }

    private CaptchaImage createRandomCaptchaImage() {
        int type = RandomUtil.randomInt(0, 4);
        if (type == 0) {
            return new CaptchaImage(CAPTCHA_TYPE_LINE,
                    CaptchaUtil.createLineCaptcha(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, CAPTCHA_CODE_COUNT,
                            LINE_INTERFERE_COUNT));
        }
        if (type == 1) {
            return new CaptchaImage(CAPTCHA_TYPE_CIRCLE,
                    CaptchaUtil.createCircleCaptcha(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, CAPTCHA_CODE_COUNT,
                            CIRCLE_INTERFERE_COUNT));
        }
        if (type == 2) {
            return new CaptchaImage(CAPTCHA_TYPE_SHEAR,
                    CaptchaUtil.createShearCaptcha(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, CAPTCHA_CODE_COUNT,
                            SHEAR_INTERFERE_THICKNESS));
        }
        return new CaptchaImage(CAPTCHA_TYPE_MATH, createMathCaptcha());
    }

    private AbstractCaptcha createMathCaptcha() {
        MathGenerator mathGenerator = new MathGenerator(1);
        int visualType = RandomUtil.randomInt(0, 3);
        if (visualType == 0) {
            return CaptchaUtil.createLineCaptcha(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, mathGenerator, LINE_INTERFERE_COUNT);
        }
        if (visualType == 1) {
            return CaptchaUtil.createCircleCaptcha(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, mathGenerator, CIRCLE_INTERFERE_COUNT);
        }
        return CaptchaUtil.createShearCaptcha(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, mathGenerator, SHEAR_INTERFERE_THICKNESS);
    }

    private boolean verifyCaptchaCode(String expected, String input) {
        if (StrUtil.isBlank(expected) || StrUtil.isBlank(input)) {
            return false;
        }
        String[] parts = expected.split(":", 2);
        if (parts.length != 2) {
            return false;
        }
        String type = parts[0];
        String code = parts[1];
        String userCode = StrUtil.trim(input);
        if (CAPTCHA_TYPE_MATH.equals(type)) {
            return new MathGenerator(1).verify(code, userCode);
        }
        return StrUtil.equalsIgnoreCase(code, userCode);
    }

    private String instruction(String type) {
        if (CAPTCHA_TYPE_MATH.equals(type)) {
            return "请输入图片中算式的计算结果";
        }
        return "请输入图片中的验证码";
    }

    private void consumeCaptchaVerification(String captchaVerification) {
        if (StrUtil.isBlank(captchaVerification)) {
            throw exception(AUTH_EMAIL_CAPTCHA_REQUIRED);
        }
        String key = captchaVerificationKey(captchaVerification);
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) {
            throw exception(AUTH_EMAIL_CAPTCHA_INVALID);
        }
        stringRedisTemplate.delete(key);
    }

    private long getCount(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        return Convert.toLong(value, 0L);
    }

    private void increment(String key, Duration ttl) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, ttl);
        }
    }

    private String emailHourKey(String email, Integer scene) {
        return KEY_PREFIX + "send:email:hour:" + scene + ":" + email;
    }

    private String ipMinuteKey() {
        return KEY_PREFIX + "send:ip:minute:" + currentIp();
    }

    private String ipHourKey() {
        return KEY_PREFIX + "send:ip:hour:" + currentIp();
    }

    private String ipDayKey() {
        return KEY_PREFIX + "send:ip:day:" + currentIp();
    }

    private String validateFailKey(String email, Integer scene) {
        return KEY_PREFIX + "validate:fail:" + scene + ":" + email;
    }

    private String captchaChallengeKey(String challengeId) {
        return KEY_PREFIX + "captcha:challenge:" + challengeId;
    }

    private String captchaVerificationKey(String captchaVerification) {
        return KEY_PREFIX + "captcha:verification:" + captchaVerification;
    }

    private String currentIp() {
        String ip = getClientIP();
        return StrUtil.blankToDefault(ip, "unknown");
    }

    private static class CaptchaImage {

        private final String type;
        private final AbstractCaptcha captcha;

        private CaptchaImage(String type, AbstractCaptcha captcha) {
            this.type = type;
            this.captcha = captcha;
        }

    }

}
