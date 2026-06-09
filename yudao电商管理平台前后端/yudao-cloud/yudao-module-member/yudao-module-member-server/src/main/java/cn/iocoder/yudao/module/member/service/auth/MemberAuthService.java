package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;

import javax.validation.Valid;

/**
 * 会员的认证 Service 接口
 *
 * 提供用户的账号密码登录、token 的校验等认证相关的功能
 *
 * @author 芋道源码
 */
public interface MemberAuthService {

    /**
     * 手机 + 密码登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO login(@Valid AppAuthLoginReqVO reqVO);

    /**
     * 邮箱 + 密码登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO emailLogin(@Valid AppAuthEmailLoginReqVO reqVO);

    /**
     * 邮箱注册
     *
     * @param reqVO 注册信息
     * @return 登录结果
     */
    AppAuthLoginRespVO emailRegister(@Valid AppAuthEmailRegisterReqVO reqVO);

    /**
     * 请求邮箱安全登录链接
     *
     * @param reqVO 邮箱信息
     */
    void sendEmailSecureLink(@Valid AppAuthEmailSecureLinkReqVO reqVO);

    AppAuthLoginRespVO emailSecureLogin(@Valid AppAuthEmailTokenReqVO reqVO);

    void sendEmailVerifyLink(Long userId, @Valid AppAuthEmailSecureLinkReqVO reqVO);

    void verifyEmail(@Valid AppAuthEmailTokenReqVO reqVO);

    void sendEmailCode(Long userId, @Valid AppAuthEmailCodeSendReqVO reqVO);

    void validateEmailCode(Long userId, @Valid AppAuthEmailCodeValidateReqVO reqVO);

    void sendPasswordResetEmail(@Valid AppAuthEmailPasswordResetSendReqVO reqVO);

    void resetPasswordByEmail(@Valid AppAuthEmailPasswordResetReqVO reqVO);

    void sendTradeLoginCode(@Valid AppAuthTradeLoginCodeSendReqVO reqVO);

    /**
     * Trade Program 登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO tradeLogin(@Valid AppAuthTradeLoginReqVO reqVO);

    /**
     * 基于 token 退出登录
     *
     * @param token token
     */
    void logout(String token);

    /**
     * 手机 + 验证码登陆
     *
     * @param reqVO    登陆信息
     * @return 登录结果
     */
    AppAuthLoginRespVO smsLogin(@Valid AppAuthSmsLoginReqVO reqVO);

    /**
     * 社交登录，使用 code 授权码
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO socialLogin(@Valid AppAuthSocialLoginReqVO reqVO);

    /**
     * 微信小程序的一键登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO weixinMiniAppLogin(AppAuthWeixinMiniAppLoginReqVO reqVO);

    /**
     * 获得社交认证 URL
     *
     * @param type 社交平台类型
     * @param redirectUri 跳转地址
     * @return 认证 URL
     */
    String getSocialAuthorizeUrl(Integer type, String redirectUri);

    /**
     * 给用户发送短信验证码
     *
     * @param userId 用户编号
     * @param reqVO 发送信息
     */
    void sendSmsCode(Long userId, AppAuthSmsSendReqVO reqVO);

    /**
     * 校验短信验证码是否正确
     *
     * @param userId 用户编号
     * @param reqVO 校验信息
     */
    void validateSmsCode(Long userId, AppAuthSmsValidateReqVO reqVO);

    /**
     * 刷新访问令牌
     *
     * @param refreshToken 刷新令牌
     * @return 登录结果
     */
    AppAuthLoginRespVO refreshToken(String refreshToken);

}
