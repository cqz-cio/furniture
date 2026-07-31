package cn.iocoder.yudao.module.system.service.inquiry;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.crm.api.inquiry.CrmWebsiteInquiryApi;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import cn.iocoder.yudao.module.system.controller.app.inquiry.vo.AppWebsiteInquirySubmitReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.framework.inquiry.config.WebsiteInquiryProperties;
import cn.iocoder.yudao.module.system.service.inquiry.mail.WebsiteInquiryMailService;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_CONFIGURATION_ERROR;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_DISABLED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_UNAUTHORIZED;

/**
 * 官网询盘 Service 实现。
 */
@Service
@Validated
@Slf4j
public class WebsiteInquiryServiceImpl implements WebsiteInquiryService {

    /**
     * 站内信表的正文上限是 1024 个字符。完整询盘已保存在 CRM，这里只保留足够识别询盘的需求摘要，
     * 避免长产品清单导致站内信插入失败。
     */
    private static final int NOTIFICATION_MESSAGE_PREVIEW_CODE_POINTS = 400;

    private static final DateTimeFormatter SUBMITTED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private WebsiteInquiryProperties properties;
    @Resource
    private TenantService tenantService;
    @Resource
    private NotifySendService notifySendService;
    @Resource
    private CrmWebsiteInquiryApi crmWebsiteInquiryApi;
    @Resource
    private WebsiteInquiryMailService websiteInquiryMailService;

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
        if (tenant == null || tenant.getContactUserId() == null) {
            throw exception(WEBSITE_INQUIRY_CONFIGURATION_ERROR);
        }

        LocalDateTime submittedAt = LocalDateTime.now();
        CrmWebsiteInquiryCreateRespDTO createResult = crmWebsiteInquiryApi.createWebsiteInquiry(
                buildCrmCreateReqDTO(reqVO, tenant.getContactUserId(), submittedAt));
        if (createResult == null || createResult.getInquiryId() == null) {
            throw exception(WEBSITE_INQUIRY_CONFIGURATION_ERROR);
        }

        // 询盘入库是主链路；提醒失败不能让网页误以为提交失败并产生重复询盘。
        if (Boolean.TRUE.equals(createResult.getCreated()) && !isBlank(properties.getTemplateCode())) {
            try {
                Long messageId = notifySendService.sendSingleNotifyToAdmin(
                        tenant.getContactUserId(), properties.getTemplateCode(),
                        buildTemplateParams(reqVO, submittedAt));
                if (messageId == null) {
                    log.warn("Website inquiry {} persisted, but the ERP notify service returned no message id",
                            createResult.getInquiryId());
                }
            } catch (RuntimeException ex) {
                log.warn("Website inquiry {} persisted, but the ERP notification failed",
                        createResult.getInquiryId(), ex);
            }
        }
        // 邮件也是入库后的旁路：配置缺失或发送失败只记录投递状态，不能回滚询盘。
        try {
            websiteInquiryMailService.ensureDeliveryAndSend(createResult.getInquiryId());
        } catch (RuntimeException ex) {
            log.warn("Website inquiry {} persisted, but the ERP mail relay could not be queued",
                    createResult.getInquiryId(), ex);
        }
        return createResult.getInquiryId();
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

    private static CrmWebsiteInquiryCreateReqDTO buildCrmCreateReqDTO(
            AppWebsiteInquirySubmitReqVO reqVO, Long ownerUserId, LocalDateTime submittedAt) {
        CrmWebsiteInquiryCreateReqDTO reqDTO = new CrmWebsiteInquiryCreateReqDTO();
        reqDTO.setExternalInquiryId(isBlank(reqVO.getExternalInquiryId())
                ? UUID.randomUUID().toString() : normalize(reqVO.getExternalInquiryId()));
        reqDTO.setOwnerUserId(ownerUserId);
        reqDTO.setContactName(normalize(reqVO.getName()));
        reqDTO.setEmail(normalize(reqVO.getEmail()));
        reqDTO.setCountryCode(normalize(reqVO.getCountryCode()));
        reqDTO.setPhone(normalize(reqVO.getPhone()));
        reqDTO.setCompanyName(normalize(reqVO.getCompanyName()));
        reqDTO.setSubject(normalize(reqVO.getSubject()));
        reqDTO.setMessage(normalizeMultiline(reqVO.getMessage()));
        reqDTO.setSourcePage(normalize(reqVO.getSourcePage()));
        reqDTO.setLocale(normalize(reqVO.getLocale()));
        reqDTO.setUtmSource(normalize(reqVO.getUtmSource()));
        reqDTO.setUtmMedium(normalize(reqVO.getUtmMedium()));
        reqDTO.setUtmCampaign(normalize(reqVO.getUtmCampaign()));
        reqDTO.setSubmittedAt(submittedAt);
        return reqDTO;
    }

    private static Map<String, Object> buildTemplateParams(
            AppWebsiteInquirySubmitReqVO reqVO, LocalDateTime submittedAt) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", display(reqVO.getName()));
        params.put("email", display(reqVO.getEmail()));
        params.put("companyName", display(reqVO.getCompanyName()));
        params.put("phone", buildPhone(reqVO.getCountryCode(), reqVO.getPhone()));
        params.put("subject", display(reqVO.getSubject()));
        params.put("message", displayNotificationMessage(reqVO.getMessage()));
        params.put("sourcePage", display(reqVO.getSourcePage()));
        params.put("locale", display(reqVO.getLocale()));
        params.put("utmSource", display(reqVO.getUtmSource()));
        params.put("utmMedium", display(reqVO.getUtmMedium()));
        params.put("utmCampaign", display(reqVO.getUtmCampaign()));
        params.put("submittedAt", submittedAt.format(SUBMITTED_AT_FORMATTER));
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

    private static String displayNotificationMessage(String value) {
        String normalized = display(value);
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= NOTIFICATION_MESSAGE_PREVIEW_CODE_POINTS) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(
                0, NOTIFICATION_MESSAGE_PREVIEW_CODE_POINTS - 1);
        return normalized.substring(0, endIndex) + "…";
    }

    private static String normalize(String value) {
        return value == null ? "" : value
                .replaceAll("[\\p{Cntrl}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeMultiline(String value) {
        return value == null ? "" : value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]+", " ")
                .trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
