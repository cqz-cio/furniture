package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyReqVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyRespVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerificationStatusRespVO;

public interface AddressVerificationProvider {

    AppAddressVerifyRespVO verify(AppAddressVerifyReqVO reqVO);

    default AppAddressVerificationStatusRespVO.ProviderStatus getStatus() {
        AppAddressVerificationStatusRespVO.ProviderStatus status = new AppAddressVerificationStatusRespVO.ProviderStatus();
        status.setSource(getClass().getSimpleName());
        status.setName(getClass().getSimpleName());
        status.setEnabled(true);
        status.setFallback(false);
        return status;
    }

}
