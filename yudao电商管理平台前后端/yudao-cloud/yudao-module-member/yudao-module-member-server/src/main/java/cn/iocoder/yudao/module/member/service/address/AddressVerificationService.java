package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyReqVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyRespVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerificationStatusRespVO;

import javax.validation.Valid;

public interface AddressVerificationService {

    AppAddressVerifyRespVO verifyAddress(@Valid AppAddressVerifyReqVO reqVO);

    AppAddressVerificationStatusRespVO getStatus();

}
