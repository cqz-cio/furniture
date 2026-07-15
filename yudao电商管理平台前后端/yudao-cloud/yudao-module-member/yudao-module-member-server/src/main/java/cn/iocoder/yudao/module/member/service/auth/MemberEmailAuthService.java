package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCodeSendReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailCodeValidateReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthEmailPasswordResetReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;

import jakarta.validation.Valid;

public interface MemberEmailAuthService {

    void sendLoginLink(String email);

    MemberUserDO loginByToken(String token);

    void sendVerifyEmail(Long userId, String email);

    void verifyEmail(String token);

    void sendCode(Long userId, @Valid AppAuthEmailCodeSendReqVO reqVO);

    void validateCode(Long userId, @Valid AppAuthEmailCodeValidateReqVO reqVO);

    void sendPasswordResetEmail(String email);

    void resetPasswordByEmail(@Valid AppAuthEmailPasswordResetReqVO reqVO);

}
