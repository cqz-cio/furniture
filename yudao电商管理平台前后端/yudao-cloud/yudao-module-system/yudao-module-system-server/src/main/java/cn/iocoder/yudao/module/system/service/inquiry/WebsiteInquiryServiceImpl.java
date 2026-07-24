package cn.iocoder.yudao.module.system.service.inquiry;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.app.inquiry.vo.AppWebsiteInquirySubmitReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.framework.inquiry.config.WebsiteInquiryProperties;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_CONFIGURATION_ERROR;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_DISABLED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_UNAUTHORIZED;

/**
 * 官网询盘 Service 实现。
 */
@Service
@Validated
public class WebsiteInquiryServiceImpl implements WebsiteInquiryService {

    private static final DateTimeFormatter SUBMITTED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private WebsiteInquiryProperties properties;
    @Resource
    private TenantService tenantService;
    @Resource
    private NotifySendService notifySendService;

    @Override
    public Long notifyInquiry(String sharedSecret, AppWebsiteInquirySubmitReqVO reqVO) {
        if (!properties.isEnabled()) {
            throw exception(WEBSITE_INQUIRY_DISABLED);
        }
        if (!isValidSecret(sharedSecret)) {
            throw exception(WEBSITE_INQUIRY_UNAUTHORIZED);
        }

        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(properties.getTenantId(), tenantId)) {
            throw exception(WEBSITE_INQUIRY_UNAUTHORIZED);
        }

        TenantDO tenant = tenantService.getTenant(tenantId);
        if (tenant == null || tenant.getContactUserId() == null
                || isBlank(properties.getTemplateCode())) {
            throw exception(WEBSITE_INQUIRY_CONFIGURATION_ERROR);
        }

        Long messageId = notifySendService.sendSingleNotifyToAdmin(
                tenant.getContactUserId(), properties.getTemplateCode(), buildTemplateParams(reqVO));
        if (messageId == null) {
            throw exception(WEBSITE_INQUIRY_CONFIGURATION_ERROR);
        }
        return messageId;
    }

    private boolean isValidSecret(String providedSecret) {
        String configuredSecret = properties.getSharedSecret();
        if (isBlank(configuredSecret) || isBlank(providedSecret)) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                providedSecret.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> buildTemplateParams(AppWebsiteInquirySubmitReqVO reqVO) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", display(reqVO.getName()));
        params.put("email", display(reqVO.getEmail()));
        params.put("companyName", display(reqVO.getCompanyName()));
        params.put("phone", buildPhone(reqVO.getCountryCode(), reqVO.getPhone()));
        params.put("subject", display(reqVO.getSubject()));
        params.put("message", display(reqVO.getMessage()));
        params.put("sourcePage", display(reqVO.getSourcePage()));
        params.put("locale", display(reqVO.getLocale()));
        params.put("utmSource", display(reqVO.getUtmSource()));
        params.put("utmMedium", display(reqVO.getUtmMedium()));
        params.put("utmCampaign", display(reqVO.getUtmCampaign()));
        params.put("submittedAt", LocalDateTime.now().format(SUBMITTED_AT_FORMATTER));
        return params;
    }

    private static String buildPhone(String countryCode, String phone) {
        String normalizedCountryCode = normalize(countryCode);
        String normalizedPhone = normalize(phone);
        if (normalizedCountryCode.isEmpty()) {
            return normalizedPhone.isEmpty() ? "-" : normalizedPhone;
        }
        if (normalizedPhone.isEmpty()) {
            return normalizedCountryCode;
        }
        return normalizedCountryCode + " " + normalizedPhone;
    }

    private static String display(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "-" : normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value
                .replaceAll("[\\p{Cntrl}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
