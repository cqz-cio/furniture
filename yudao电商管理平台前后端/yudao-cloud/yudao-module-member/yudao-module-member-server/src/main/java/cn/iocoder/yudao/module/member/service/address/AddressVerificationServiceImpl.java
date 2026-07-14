package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyReqVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyRespVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerificationStatusRespVO;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Validated
public class AddressVerificationServiceImpl implements AddressVerificationService {

    @Resource
    private List<AddressVerificationProvider> providers = Collections.singletonList(new LocalAddressVerificationProvider());

    public AddressVerificationServiceImpl() {
    }

    AddressVerificationServiceImpl(List<AddressVerificationProvider> providers) {
        this.providers = providers;
    }

    @Override
    public AppAddressVerifyRespVO verifyAddress(AppAddressVerifyReqVO reqVO) {
        boolean providerFailed = false;
        for (AddressVerificationProvider provider : getOrderedProviders()) {
            try {
                AppAddressVerifyRespVO respVO = provider.verify(reqVO);
                if (respVO != null) {
                    if (providerFailed && (respVO.getProviderStatus() == null || respVO.getProviderStatus().trim().isEmpty())) {
                        respVO.setProviderStatus("fallback");
                    }
                    return respVO;
                }
            } catch (Exception ignored) {
                providerFailed = true;
                // Try the next provider. The local provider is the final fallback.
            }
        }
        AppAddressVerifyRespVO respVO = new LocalAddressVerificationProvider().verify(reqVO);
        if (providerFailed) {
            respVO.setProviderStatus("fallback");
        }
        return respVO;
    }

    @Override
    public AppAddressVerificationStatusRespVO getStatus() {
        List<AppAddressVerificationStatusRespVO.ProviderStatus> providerStatuses = getOrderedProviders().stream()
                .map(AddressVerificationProvider::getStatus)
                .collect(Collectors.toList());
        boolean hasPrimaryProvider = providerStatuses.stream()
                .anyMatch(provider -> Boolean.TRUE.equals(provider.getEnabled())
                        && !Boolean.TRUE.equals(provider.getFallback()));

        AppAddressVerificationStatusRespVO respVO = new AppAddressVerificationStatusRespVO();
        respVO.setProviders(providerStatuses);
        respVO.setFallbackActive(!hasPrimaryProvider);
        respVO.setMode(hasPrimaryProvider ? "primary" : "fallback");
        return respVO;
    }

    private List<AddressVerificationProvider> getOrderedProviders() {
        List<AddressVerificationProvider> orderedProviders = new ArrayList<>(
                providers == null ? Collections.emptyList() : providers);
        AnnotationAwareOrderComparator.sort(orderedProviders);
        return orderedProviders;
    }

}
