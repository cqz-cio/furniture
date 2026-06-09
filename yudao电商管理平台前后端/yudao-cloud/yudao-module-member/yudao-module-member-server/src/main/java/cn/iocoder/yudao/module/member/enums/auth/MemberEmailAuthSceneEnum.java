package cn.iocoder.yudao.module.member.enums.auth;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum MemberEmailAuthSceneEnum implements ArrayValuable<Integer> {

    EMAIL_VERIFY(1, "member-email-verify-link", "/auth/email/verify", Duration.ofHours(24), true),
    SECURE_LOGIN(2, "member-email-secure-login-link", "/auth/email-login", Duration.ofMinutes(30), true),
    RESET_PASSWORD(3, "member-email-reset-password-link", "/auth/reset-password", Duration.ofMinutes(30), true),
    UPDATE_PASSWORD(4, "member-email-update-password-code", null, Duration.ofMinutes(10), false),
    GENERAL_CODE(5, "member-email-code", null, Duration.ofMinutes(10), false),
    TRADE_LOGIN_CODE(6, "member-email-code", null, Duration.ofMinutes(10), false);

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(MemberEmailAuthSceneEnum::getScene).toArray(Integer[]::new);

    private final Integer scene;
    private final String templateCode;
    private final String linkPath;
    private final Duration expireDuration;
    private final boolean tokenScene;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static MemberEmailAuthSceneEnum getByScene(Integer scene) {
        return ArrayUtil.firstMatch(sceneEnum -> sceneEnum.getScene().equals(scene), values());
    }

}
