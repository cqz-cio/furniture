package cn.iocoder.yudao.module.member.controller.app.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;
import cn.iocoder.yudao.module.member.convert.auth.AuthConvert;
import cn.iocoder.yudao.module.member.service.auth.MemberAuthService;
import cn.iocoder.yudao.module.member.service.auth.MemberEmailAuthSecurityService;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxJsapiSignatureRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 APP - 认证")
@RestController
@RequestMapping("/member/auth")
@Validated
@Slf4j
public class AppAuthController {

    @Resource
    private MemberAuthService authService;
    @Resource
    private MemberEmailAuthSecurityService memberEmailAuthSecurityService;

    @Resource
    private SocialClientApi socialClientApi;

    @Resource
    private SecurityProperties securityProperties;

    @PostMapping("/login")
    @Operation(summary = "使用手机 + 密码登录")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> login(@RequestBody @Valid AppAuthLoginReqVO reqVO) {
        return success(authService.login(reqVO));
    }

    @PostMapping("/email-login")
    @Operation(summary = "使用邮箱 + 密码登录")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> emailLogin(@RequestBody @Valid AppAuthEmailLoginReqVO reqVO) {
        return success(authService.emailLogin(reqVO));
    }

    @PostMapping("/email-register")
    @Operation(summary = "使用邮箱注册")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> emailRegister(@RequestBody @Valid AppAuthEmailRegisterReqVO reqVO) {
        return success(authService.emailRegister(reqVO));
    }

    @PostMapping("/email-secure-link")
    @Operation(summary = "请求邮箱安全登录链接")
    @PermitAll
    public CommonResult<Boolean> sendEmailSecureLink(@RequestBody @Valid AppAuthEmailSecureLinkReqVO reqVO) {
        authService.sendEmailSecureLink(reqVO);
        return success(true);
    }

    @PostMapping("/email-secure-login")
    @Operation(summary = "使用邮箱安全登录链接登录")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> emailSecureLogin(@RequestBody @Valid AppAuthEmailTokenReqVO reqVO) {
        return success(authService.emailSecureLogin(reqVO));
    }

    @PostMapping("/email-verify-link")
    @Operation(summary = "发送邮箱验证链接")
    @PermitAll
    public CommonResult<Boolean> sendEmailVerifyLink(@RequestBody @Valid AppAuthEmailSecureLinkReqVO reqVO) {
        authService.sendEmailVerifyLink(getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "验证邮箱")
    @PermitAll
    public CommonResult<Boolean> verifyEmail(@RequestBody @Valid AppAuthEmailTokenReqVO reqVO) {
        authService.verifyEmail(reqVO);
        return success(true);
    }

    @PostMapping("/send-email-code")
    @Operation(summary = "发送邮箱验证码")
    @PermitAll
    public CommonResult<Boolean> sendEmailCode(@RequestBody @Valid AppAuthEmailCodeSendReqVO reqVO) {
        authService.sendEmailCode(getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/validate-email-code")
    @Operation(summary = "校验邮箱验证码")
    @PermitAll
    public CommonResult<Boolean> validateEmailCode(@RequestBody @Valid AppAuthEmailCodeValidateReqVO reqVO) {
        authService.validateEmailCode(getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/email-captcha/challenge")
    @Operation(summary = "创建邮箱验证码图形挑战")
    @PermitAll
    public CommonResult<AppAuthEmailCaptchaChallengeRespVO> createEmailCaptchaChallenge() {
        return success(memberEmailAuthSecurityService.createCaptchaChallenge());
    }

    @PostMapping("/email-captcha/verify")
    @Operation(summary = "校验邮箱验证码图形挑战")
    @PermitAll
    public CommonResult<AppAuthEmailCaptchaVerifyRespVO> verifyEmailCaptchaChallenge(
            @RequestBody @Valid AppAuthEmailCaptchaVerifyReqVO reqVO) {
        return success(memberEmailAuthSecurityService.verifyCaptchaChallenge(reqVO));
    }

    @PostMapping("/password-reset-email")
    @Operation(summary = "发送邮箱重置密码链接")
    @PermitAll
    public CommonResult<Boolean> sendPasswordResetEmail(@RequestBody @Valid AppAuthEmailPasswordResetSendReqVO reqVO) {
        authService.sendPasswordResetEmail(reqVO);
        return success(true);
    }

    @PutMapping("/reset-password-by-email")
    @Operation(summary = "使用邮箱链接重置密码")
    @PermitAll
    public CommonResult<Boolean> resetPasswordByEmail(@RequestBody @Valid AppAuthEmailPasswordResetReqVO reqVO) {
        authService.resetPasswordByEmail(reqVO);
        return success(true);
    }

    @PostMapping("/trade-login-code")
    @Operation(summary = "Send Trade Program login email code")
    @PermitAll
    public CommonResult<Boolean> sendTradeLoginCode(@RequestBody @Valid AppAuthTradeLoginCodeSendReqVO reqVO) {
        authService.sendTradeLoginCode(reqVO);
        return success(true);
    }

    @PostMapping("/trade-login")
    @Operation(summary = "Trade Program 登录")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> tradeLogin(@RequestBody @Valid AppAuthTradeLoginReqVO reqVO) {
        return success(authService.tradeLogin(reqVO));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出系统")
    @PermitAll
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isNotBlank(token)) {
            authService.logout(token);
        }
        return success(true);
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "刷新令牌")
    @Parameter(name = "refreshToken", description = "刷新令牌", required = true)
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        return success(authService.refreshToken(refreshToken));
    }

    // ========== 短信登录相关 ==========

    @PostMapping("/sms-login")
    @Operation(summary = "使用手机 + 验证码登录")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> smsLogin(@RequestBody @Valid AppAuthSmsLoginReqVO reqVO) {
        return success(authService.smsLogin(reqVO));
    }

    @PostMapping("/send-sms-code")
    @Operation(summary = "发送手机验证码")
    @PermitAll
    public CommonResult<Boolean> sendSmsCode(@RequestBody @Valid AppAuthSmsSendReqVO reqVO) {
        authService.sendSmsCode(getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/validate-sms-code")
    @Operation(summary = "校验手机验证码")
    @PermitAll
    public CommonResult<Boolean> validateSmsCode(@RequestBody @Valid AppAuthSmsValidateReqVO reqVO) {
        authService.validateSmsCode(getLoginUserId(), reqVO);
        return success(true);
    }

    // ========== 社交登录相关 ==========

    @GetMapping("/social-auth-redirect")
    @Operation(summary = "社交授权的跳转")
    @Parameters({
            @Parameter(name = "type", description = "社交类型", required = true),
            @Parameter(name = "redirectUri", description = "回调路径")
    })
    @PermitAll
    public CommonResult<String> socialAuthRedirect(@RequestParam("type") Integer type,
                                                   @RequestParam("redirectUri") String redirectUri) {
        return CommonResult.success(authService.getSocialAuthorizeUrl(type, redirectUri));
    }

    @PostMapping("/social-login")
    @Operation(summary = "社交快捷登录，使用 code 授权码", description = "适合未登录的用户，但是社交账号已绑定用户")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> socialLogin(@RequestBody @Valid AppAuthSocialLoginReqVO reqVO) {
        return success(authService.socialLogin(reqVO));
    }

    @PostMapping("/weixin-mini-app-login")
    @Operation(summary = "微信小程序的一键登录")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> weixinMiniAppLogin(@RequestBody @Valid AppAuthWeixinMiniAppLoginReqVO reqVO) {
        return success(authService.weixinMiniAppLogin(reqVO));
    }

    @PostMapping("/create-weixin-jsapi-signature")
    @Operation(summary = "创建微信 JS SDK 初始化所需的签名",
            description = "参考 https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/JS-SDK.html 文档")
    @PermitAll
    public CommonResult<SocialWxJsapiSignatureRespDTO> createWeixinMpJsapiSignature(@RequestParam("url") String url) {
        SocialWxJsapiSignatureRespDTO signature = socialClientApi.createWxMpJsapiSignature(
                UserTypeEnum.MEMBER.getValue(), url).getCheckedData();
        return success(AuthConvert.INSTANCE.convert(signature));
    }

}
