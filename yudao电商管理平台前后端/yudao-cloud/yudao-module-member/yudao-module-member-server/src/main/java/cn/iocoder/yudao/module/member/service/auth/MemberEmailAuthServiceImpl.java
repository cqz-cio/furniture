package cn.iocoder.yudao.module.member.service.auth;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCodeSendReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCodeValidateReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailPasswordResetReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.auth.MemberEmailAuthDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.auth.MemberEmailAuthMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.auth.MemberEmailAuthSceneEnum;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.mail.MailSendApi;
import cn.iocoder.yudao.module.system.api.mail.dto.MailSendSingleToUserReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

@Service
@Validated
@Slf4j
public class MemberEmailAuthServiceImpl implements MemberEmailAuthService {

    private static final int CREDENTIAL_TYPE_TOKEN = 1;
    private static final int CREDENTIAL_TYPE_CODE = 2;
    private static final long SEND_FREQUENCY_MILLIS = 60_000L;

    @Resource
    private MemberEmailAuthMapper memberEmailAuthMapper;
    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MemberUserService userService;
    @Resource
    private MailSendApi mailSendApi;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private MemberEmailAuthSecurityService memberEmailAuthSecurityService;

    @Value("${yudao.web.app-ui.url:http://127.0.0.1:5173}")
    private String appUiUrl;

    @Override
    public void sendLoginLink(String email) {
        email = normalizeEmail(email);
        MemberUserDO user = userService.getUserByEmail(email);
        if (user == null) {
            log.info("[sendLoginLink][email({}) not exists, skip sending]", email);
            return;
        }
        createTokenAndSendMail(user.getId(), email, MemberEmailAuthSceneEnum.SECURE_LOGIN);
    }

    @Override
    public MemberUserDO loginByToken(String token) {
        MemberEmailAuthDO auth = useToken(token, MemberEmailAuthSceneEnum.SECURE_LOGIN);
        MemberUserDO user = userService.getUser(auth.getUserId());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        return user;
    }

    @Override
    public void sendVerifyEmail(Long userId, String email) {
        email = normalizeEmail(email);
        if (userId != null) {
            MemberUserDO user = userService.getUser(userId);
            if (user == null || StrUtil.isBlank(user.getEmail())) {
                throw exception(USER_EMAIL_NOT_EXISTS);
            }
            email = normalizeEmail(user.getEmail());
        } else {
            MemberUserDO user = userService.getUserByEmail(email);
            if (user == null) {
                throw exception(USER_EMAIL_NOT_EXISTS);
            }
            userId = user.getId();
        }
        createTokenAndSendMail(userId, email, MemberEmailAuthSceneEnum.EMAIL_VERIFY);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyEmail(String token) {
        MemberEmailAuthDO auth = useToken(token, MemberEmailAuthSceneEnum.EMAIL_VERIFY);
        memberUserMapper.updateById(MemberUserDO.builder().id(auth.getUserId())
                .emailVerified(true).emailVerifiedTime(LocalDateTime.now()).build());
    }

    @Override
    public void sendCode(Long userId, AppAuthEmailCodeSendReqVO reqVO) {
        MemberEmailAuthSceneEnum sceneEnum = validateCodeScene(reqVO.getScene());
        String email = normalizeEmail(reqVO.getEmail());
        if (StrUtil.isBlank(email) && userId != null) {
            MemberUserDO user = userService.getUser(userId);
            email = user != null ? user.getEmail() : null;
        }
        if (StrUtil.isBlank(email)) {
            throw exception(USER_EMAIL_NOT_EXISTS);
        }
        memberEmailAuthSecurityService.validateBeforeSend(email, sceneEnum.getScene(), reqVO.getCaptchaVerification());
        String code = createCredential(userId, email, sceneEnum, CREDENTIAL_TYPE_CODE);
        memberEmailAuthSecurityService.recordSend(email, sceneEnum.getScene());
        memberEmailAuthSecurityService.clearValidateFailures(email, sceneEnum.getScene());
        Map<String, Object> params = new HashMap<>();
        params.put("code", code);
        params.put("expireMinutes", sceneEnum.getExpireDuration().toMinutes());
        sendMail(userId, email, sceneEnum, params);
    }

    @Override
    public void validateCode(Long userId, AppAuthEmailCodeValidateReqVO reqVO) {
        MemberEmailAuthSceneEnum sceneEnum = validateCodeScene(reqVO.getScene());
        String email = normalizeEmail(reqVO.getEmail());
        if (StrUtil.isBlank(email) && userId != null) {
            MemberUserDO user = userService.getUser(userId);
            email = user != null ? user.getEmail() : null;
        }
        if (StrUtil.isBlank(email)) {
            throw exception(USER_EMAIL_NOT_EXISTS);
        }
        memberEmailAuthSecurityService.validateBeforeCheckCode(email, sceneEnum.getScene());
        try {
            useCredential(email, reqVO.getCode(), sceneEnum, CREDENTIAL_TYPE_CODE);
            memberEmailAuthSecurityService.clearValidateFailures(email, sceneEnum.getScene());
        } catch (ServiceException e) {
            if (AUTH_EMAIL_CREDENTIAL_NOT_FOUND.getCode().equals(e.getCode())) {
                memberEmailAuthSecurityService.recordValidateFailure(email, sceneEnum.getScene());
            }
            throw e;
        }
    }

    @Override
    public void sendPasswordResetEmail(String email) {
        email = normalizeEmail(email);
        MemberUserDO user = userService.getUserByEmail(email);
        if (user == null) {
            log.info("[sendPasswordResetEmail][email({}) not exists, skip sending]", email);
            return;
        }
        createTokenAndSendMail(user.getId(), email, MemberEmailAuthSceneEnum.RESET_PASSWORD);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPasswordByEmail(AppAuthEmailPasswordResetReqVO reqVO) {
        MemberEmailAuthDO auth = useToken(reqVO.getToken(), MemberEmailAuthSceneEnum.RESET_PASSWORD);
        memberUserMapper.updateById(MemberUserDO.builder().id(auth.getUserId())
                .password(passwordEncoder.encode(reqVO.getPassword())).build());
    }

    private void createTokenAndSendMail(Long userId, String email, MemberEmailAuthSceneEnum sceneEnum) {
        String token = createCredential(userId, email, sceneEnum, CREDENTIAL_TYPE_TOKEN);
        String link = buildLink(sceneEnum, token);
        Map<String, Object> params = new HashMap<>();
        params.put("link", link);
        params.put("token", token);
        params.put("expireMinutes", sceneEnum.getExpireDuration().toMinutes());
        sendMail(userId, email, sceneEnum, params);
    }

    private String createCredential(Long userId, String email, MemberEmailAuthSceneEnum sceneEnum, Integer credentialType) {
        checkSendFrequency(email, sceneEnum, credentialType);
        String credential = credentialType.equals(CREDENTIAL_TYPE_TOKEN)
                ? IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID()
                : RandomUtil.randomNumbers(6);
        memberEmailAuthMapper.insert(MemberEmailAuthDO.builder()
                .userId(userId)
                .email(email)
                .scene(sceneEnum.getScene())
                .credentialType(credentialType)
                .credentialHash(hash(credential))
                .expiresTime(LocalDateTime.now().plus(sceneEnum.getExpireDuration()))
                .used(false)
                .createIp(getClientIP())
                .build());
        return credential;
    }

    private void checkSendFrequency(String email, MemberEmailAuthSceneEnum sceneEnum, Integer credentialType) {
        MemberEmailAuthDO last = memberEmailAuthMapper.selectLastByEmail(email, sceneEnum.getScene(), credentialType);
        if (last != null && last.getCreateTime() != null
                && LocalDateTimeUtil.between(last.getCreateTime(), LocalDateTime.now()).toMillis() < SEND_FREQUENCY_MILLIS) {
            throw exception(AUTH_EMAIL_CODE_SEND_TOO_FAST);
        }
    }

    private MemberEmailAuthDO useToken(String token, MemberEmailAuthSceneEnum sceneEnum) {
        return useCredential(null, token, sceneEnum, CREDENTIAL_TYPE_TOKEN);
    }

    private MemberEmailAuthDO useCredential(String email, String credential,
                                            MemberEmailAuthSceneEnum sceneEnum, Integer credentialType) {
        MemberEmailAuthDO auth = memberEmailAuthMapper.selectByCredentialHash(
                hash(credential), sceneEnum.getScene(), credentialType);
        if (auth == null || (email != null && !StrUtil.equals(auth.getEmail(), normalizeEmail(email)))) {
            throw exception(AUTH_EMAIL_CREDENTIAL_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(auth.getUsed())) {
            throw exception(AUTH_EMAIL_CREDENTIAL_USED);
        }
        if (auth.getExpiresTime().isBefore(LocalDateTime.now())) {
            throw exception(AUTH_EMAIL_CREDENTIAL_EXPIRED);
        }
        memberEmailAuthMapper.updateById(MemberEmailAuthDO.builder().id(auth.getId())
                .used(true).usedTime(LocalDateTime.now()).usedIp(getClientIP()).build());
        return auth;
    }

    private MemberEmailAuthSceneEnum validateCodeScene(Integer scene) {
        MemberEmailAuthSceneEnum sceneEnum = MemberEmailAuthSceneEnum.getByScene(scene);
        if (sceneEnum == null || sceneEnum.isTokenScene()) {
            throw exception(AUTH_EMAIL_SCENE_NOT_SUPPORTED);
        }
        return sceneEnum;
    }

    private void sendMail(Long userId, String email, MemberEmailAuthSceneEnum sceneEnum, Map<String, Object> params) {
        MailSendSingleToUserReqDTO reqDTO = new MailSendSingleToUserReqDTO();
        reqDTO.setUserId(userId);
        reqDTO.setToMails(Collections.singletonList(email));
        reqDTO.setTemplateCode(sceneEnum.getTemplateCode());
        reqDTO.setTemplateParams(params);
        mailSendApi.sendSingleMailToMember(reqDTO).checkError();
    }

    private String buildLink(MemberEmailAuthSceneEnum sceneEnum, String token) {
        return StrUtil.removeSuffix(appUiUrl, "/") + sceneEnum.getLinkPath() + "?token=" + token;
    }

    private String normalizeEmail(String email) {
        return StrUtil.isBlank(email) ? email : StrUtil.trim(email).toLowerCase();
    }

    private String hash(String credential) {
        return SecureUtil.sha256(credential);
    }

}
