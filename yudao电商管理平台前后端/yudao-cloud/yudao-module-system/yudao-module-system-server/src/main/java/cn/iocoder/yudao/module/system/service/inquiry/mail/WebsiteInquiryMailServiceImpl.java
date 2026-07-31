package cn.iocoder.yudao.module.system.service.inquiry.mail;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.crm.api.inquiry.CrmWebsiteInquiryApi;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.inquiry.vo.WebsiteInquiryMailConfigRespVO;
import cn.iocoder.yudao.module.system.controller.admin.inquiry.vo.WebsiteInquiryMailConfigSaveReqVO;
import cn.iocoder.yudao.module.system.controller.admin.inquiry.vo.WebsiteInquiryMailDeliveryRespVO;
import cn.iocoder.yudao.module.system.controller.admin.mail.vo.template.MailTemplateSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.inquiry.WebsiteInquiryMailConfigDO;
import cn.iocoder.yudao.module.system.dal.dataobject.inquiry.WebsiteInquiryMailDeliveryDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailAccountDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailLogDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailTemplateDO;
import cn.iocoder.yudao.module.system.dal.mysql.inquiry.WebsiteInquiryMailConfigMapper;
import cn.iocoder.yudao.module.system.dal.mysql.inquiry.WebsiteInquiryMailDeliveryMapper;
import cn.iocoder.yudao.module.system.enums.inquiry.WebsiteInquiryMailDeliveryStatusEnum;
import cn.iocoder.yudao.module.system.enums.mail.MailSendStatusEnum;
import cn.iocoder.yudao.module.system.service.mail.MailAccountService;
import cn.iocoder.yudao.module.system.service.mail.MailLogService;
import cn.iocoder.yudao.module.system.service.mail.MailSendService;
import cn.iocoder.yudao.module.system.service.mail.MailTemplateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_MAIL_CONFIGURATION_ERROR;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_MAIL_TEMPLATE_INVALID;

/**
 * 官网询盘邮件转发 Service 实现。
 */
@Service
@Validated
@Slf4j
public class WebsiteInquiryMailServiceImpl implements WebsiteInquiryMailService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int RETRY_BATCH_SIZE = 20;
    private static final Duration CONFIG_RECHECK_DELAY = Duration.ofMinutes(1);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
            Duration.ofHours(2), Duration.ofHours(6));

    @Resource
    private WebsiteInquiryMailConfigMapper configMapper;
    @Resource
    private WebsiteInquiryMailDeliveryMapper deliveryMapper;
    @Resource
    private CrmWebsiteInquiryApi crmWebsiteInquiryApi;
    @Resource
    private MailAccountService mailAccountService;
    @Resource
    private MailTemplateService mailTemplateService;
    @Resource
    private MailSendService mailSendService;
    @Resource
    private MailLogService mailLogService;
    @Resource
    private WebsiteInquiryMailTemplateRenderer templateRenderer;

    @Override
    public WebsiteInquiryMailConfigRespVO getConfig() {
        WebsiteInquiryMailConfigDO config = configMapper.selectCurrentTenantConfig();
        WebsiteInquiryMailConfigRespVO respVO = new WebsiteInquiryMailConfigRespVO();
        respVO.setAvailableVariables(templateRenderer.getAvailableVariables());
        if (config == null) {
            respVO.setConfigured(false);
            respVO.setEnabled(false);
            respVO.setSenderName(WebsiteInquiryMailTemplateRenderer.DEFAULT_SENDER_NAME);
            respVO.setSubjectTemplate(WebsiteInquiryMailTemplateRenderer.DEFAULT_SUBJECT);
            respVO.setContentTemplate(WebsiteInquiryMailTemplateRenderer.DEFAULT_CONTENT);
            return respVO;
        }
        respVO.setConfigured(hasCompleteConfiguration(config));
        respVO.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        respVO.setRecipientEmail(config.getRecipientEmail());
        respVO.setMailAccountId(config.getMailAccountId());
        respVO.setSenderName(config.getSenderName());
        respVO.setSubjectTemplate(config.getSubjectTemplate());
        respVO.setContentTemplate(config.getContentTemplate());
        respVO.setErpBaseUrl(config.getErpBaseUrl());
        respVO.setUpdateTime(config.getUpdateTime());
        MailAccountDO account = config.getMailAccountId() == null
                ? null : mailAccountService.getMailAccount(config.getMailAccountId());
        respVO.setSenderEmail(account == null ? null : account.getMail());
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(WebsiteInquiryMailConfigSaveReqVO reqVO) {
        normalize(reqVO);
        try {
            templateRenderer.validateTemplates(reqVO.getSubjectTemplate(), reqVO.getContentTemplate());
        } catch (IllegalArgumentException ex) {
            throw exception(WEBSITE_INQUIRY_MAIL_TEMPLATE_INVALID,
                    StrUtil.blankToDefault(ex.getMessage(), "模板校验失败"));
        }

        MailAccountDO account = null;
        if (reqVO.getMailAccountId() != null) {
            account = mailAccountService.getMailAccount(reqVO.getMailAccountId());
            if (account == null) {
                throw exception(WEBSITE_INQUIRY_MAIL_CONFIGURATION_ERROR, "发件邮箱账号不存在");
            }
        }
        if (Boolean.TRUE.equals(reqVO.getEnabled())) {
            validateEnabledConfiguration(reqVO, account);
        }

        WebsiteInquiryMailConfigDO config = configMapper.selectCurrentTenantConfig();
        boolean create = config == null;
        if (create) {
            config = new WebsiteInquiryMailConfigDO();
        }
        config.setEnabled(Boolean.TRUE.equals(reqVO.getEnabled()));
        config.setRecipientEmail(reqVO.getRecipientEmail());
        config.setMailAccountId(reqVO.getMailAccountId());
        config.setSenderName(reqVO.getSenderName());
        config.setSubjectTemplate(reqVO.getSubjectTemplate());
        config.setContentTemplate(reqVO.getContentTemplate());
        config.setErpBaseUrl(reqVO.getErpBaseUrl());

        if (account != null) {
            config.setMailTemplateId(syncSystemMailTemplate(config));
        } else if (config.getMailTemplateId() != null) {
            disableSystemMailTemplate(config);
        }
        if (create) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
    }

    @Override
    public Long sendTestMail() {
        WebsiteInquiryMailConfigDO config = requireEnabledConfiguration();
        WebsiteInquiryMailTemplateRenderer.RenderedMail mail = templateRenderer.render(
                config, WebsiteInquiryMailTemplateRenderer.sampleInquiry("sample.customer@example.com"));
        return mailSendService.sendPreparedMail(
                List.of(config.getRecipientEmail()), null, null,
                List.of("sample.customer@example.com"), null, null,
                templateCode(), testLogParams(), mail.title(), mail.htmlContent(), null);
    }

    @Override
    public void ensureDeliveryAndSend(Long inquiryId) {
        CrmWebsiteInquiryRespDTO inquiry = getInquiry(inquiryId);
        WebsiteInquiryMailDeliveryDO delivery = deliveryMapper.selectByInquiryId(inquiryId);
        if (delivery == null) {
            delivery = new WebsiteInquiryMailDeliveryDO();
            delivery.setInquiryId(inquiryId);
            delivery.setExternalInquiryId(StrUtil.blankToDefault(
                    inquiry.getExternalInquiryId(), String.valueOf(inquiryId)));
            delivery.setRecipientEmail("");
            delivery.setCustomerEmail(StrUtil.blankToDefault(inquiry.getEmail(), ""));
            delivery.setStatus(WebsiteInquiryMailDeliveryStatusEnum.PENDING.getStatus());
            delivery.setAttemptCount(0);
            delivery.setNextRetryTime(LocalDateTime.now());
            delivery.setLastError("");
            try {
                deliveryMapper.insert(delivery);
            } catch (DuplicateKeyException ignored) {
                delivery = deliveryMapper.selectByInquiryId(inquiryId);
            }
        }
        if (delivery != null && !WebsiteInquiryMailDeliveryStatusEnum.SUCCESS.getStatus()
                .equals(delivery.getStatus())) {
            sendDelivery(delivery, inquiry);
        }
    }

    @Override
    public WebsiteInquiryMailDeliveryRespVO getDelivery(Long inquiryId) {
        WebsiteInquiryMailDeliveryDO delivery = deliveryMapper.selectByInquiryId(inquiryId);
        if (delivery == null) {
            return null;
        }
        WebsiteInquiryMailDeliveryRespVO respVO = new WebsiteInquiryMailDeliveryRespVO();
        respVO.setId(delivery.getId());
        respVO.setInquiryId(delivery.getInquiryId());
        respVO.setRecipientEmail(delivery.getRecipientEmail());
        respVO.setCustomerEmail(delivery.getCustomerEmail());
        respVO.setStatus(delivery.getStatus());
        respVO.setAttemptCount(delivery.getAttemptCount());
        respVO.setMailLogId(delivery.getMailLogId());
        respVO.setNextRetryTime(delivery.getNextRetryTime());
        respVO.setSentTime(delivery.getSentTime());
        respVO.setLastError(delivery.getLastError());
        respVO.setUpdateTime(delivery.getUpdateTime());
        return respVO;
    }

    @Override
    public void resend(Long inquiryId) {
        WebsiteInquiryMailDeliveryDO delivery = deliveryMapper.selectByInquiryId(inquiryId);
        if (delivery == null) {
            // 兼容功能上线前已经存在的历史询盘。
            ensureDeliveryAndSend(inquiryId);
            return;
        }
        deliveryMapper.resetForManualResend(delivery.getId());
        sendDelivery(deliveryMapper.selectById(delivery.getId()), getInquiry(inquiryId));
    }

    @Override
    public void retryDueDeliveries() {
        List<WebsiteInquiryMailDeliveryDO> deliveries = deliveryMapper.selectRetryable(
                LocalDateTime.now(), MAX_ATTEMPTS, RETRY_BATCH_SIZE);
        for (WebsiteInquiryMailDeliveryDO delivery : deliveries) {
            try {
                sendDelivery(delivery, getInquiry(delivery.getInquiryId()));
            } catch (RuntimeException ex) {
                log.warn("[retryDueDeliveries][询盘邮件投递({})重试失败]", delivery.getId(), ex);
            }
        }
    }

    @Override
    public void onMailFinished(Long deliveryId, Long mailLogId) {
        WebsiteInquiryMailDeliveryDO delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            log.warn("[onMailFinished][询盘邮件投递({})不存在，邮件日志({})]", deliveryId, mailLogId);
            return;
        }
        MailLogDO mailLog = mailLogService.getMailLog(mailLogId);
        if (mailLog != null && MailSendStatusEnum.SUCCESS.getStatus() == mailLog.getSendStatus()) {
            deliveryMapper.markResult(deliveryId, mailLogId,
                    WebsiteInquiryMailDeliveryStatusEnum.SUCCESS.getStatus(),
                    mailLog.getSendTime() != null ? mailLog.getSendTime() : LocalDateTime.now(),
                    null, "");
            return;
        }
        String error = mailLog == null ? "邮件日志不存在"
                : StrUtil.blankToDefault(mailLog.getSendException(), "邮件发送失败");
        markFailure(deliveryId, mailLogId, error);
    }

    private void sendDelivery(WebsiteInquiryMailDeliveryDO delivery,
                              CrmWebsiteInquiryRespDTO inquiry) {
        WebsiteInquiryMailConfigDO config = configMapper.selectCurrentTenantConfig();
        if (!isEnabledAndComplete(config)) {
            deliveryMapper.markResult(delivery.getId(), delivery.getMailLogId(),
                    WebsiteInquiryMailDeliveryStatusEnum.CONFIG_REQUIRED.getStatus(),
                    null, LocalDateTime.now().plus(CONFIG_RECHECK_DELAY),
                    configRequiredReason(config));
            return;
        }

        int nextAttempt = Math.max(1, defaultInt(delivery.getAttemptCount()) + 1);
        LocalDateTime retryDeadline = LocalDateTime.now().plus(retryDelay(nextAttempt));
        if (deliveryMapper.claim(delivery.getId(), MAX_ATTEMPTS, retryDeadline) == 0) {
            return;
        }

        try {
            WebsiteInquiryMailTemplateRenderer.RenderedMail mail =
                    templateRenderer.render(config, inquiry);
            Map<String, Object> logParams = new LinkedHashMap<>();
            logParams.put("inquiryId", inquiry.getId());
            logParams.put("externalInquiryId",
                    StrUtil.blankToDefault(inquiry.getExternalInquiryId(), ""));
            Long mailLogId = mailSendService.sendPreparedMail(
                    List.of(config.getRecipientEmail()), null, null,
                    List.of(inquiry.getEmail()), null, null, templateCode(), logParams,
                    mail.title(), mail.htmlContent(), delivery.getId());
            deliveryMapper.markQueuedIfSending(delivery.getId(), mailLogId,
                    config.getRecipientEmail(), inquiry.getEmail());

            MailLogDO mailLog = mailLogService.getMailLog(mailLogId);
            if (mailLog != null && MailSendStatusEnum.IGNORE.getStatus() == mailLog.getSendStatus()) {
                deliveryMapper.markResult(delivery.getId(), mailLogId,
                        WebsiteInquiryMailDeliveryStatusEnum.CONFIG_REQUIRED.getStatus(),
                        null, LocalDateTime.now().plus(CONFIG_RECHECK_DELAY),
                        "询盘邮件模板当前已禁用");
            }
        } catch (RuntimeException ex) {
            markFailure(delivery.getId(), null, ex);
            throw ex;
        }
    }

    private WebsiteInquiryMailConfigDO requireEnabledConfiguration() {
        WebsiteInquiryMailConfigDO config = configMapper.selectCurrentTenantConfig();
        if (!isEnabledAndComplete(config)) {
            throw exception(WEBSITE_INQUIRY_MAIL_CONFIGURATION_ERROR,
                    configRequiredReason(config));
        }
        return config;
    }

    private Long syncSystemMailTemplate(WebsiteInquiryMailConfigDO config) {
        String code = templateCode();
        MailTemplateDO existing = config.getMailTemplateId() == null
                ? mailTemplateService.getMailTemplateByCodeFromCache(code)
                : mailTemplateService.getMailTemplate(config.getMailTemplateId());
        MailTemplateSaveReqVO template = buildMailTemplateSaveReqVO(config, existing, code);
        if (existing == null) {
            return mailTemplateService.createMailTemplate(template);
        }
        template.setId(existing.getId());
        mailTemplateService.updateMailTemplate(template);
        return existing.getId();
    }

    private void disableSystemMailTemplate(WebsiteInquiryMailConfigDO config) {
        MailTemplateDO existing = mailTemplateService.getMailTemplate(config.getMailTemplateId());
        if (existing == null) {
            config.setMailTemplateId(null);
            return;
        }
        MailTemplateSaveReqVO template = buildMailTemplateSaveReqVO(config, existing, existing.getCode());
        template.setId(existing.getId());
        template.setAccountId(existing.getAccountId());
        template.setStatus(CommonStatusEnum.DISABLE.getStatus());
        mailTemplateService.updateMailTemplate(template);
    }

    private MailTemplateSaveReqVO buildMailTemplateSaveReqVO(
            WebsiteInquiryMailConfigDO config, MailTemplateDO existing, String code) {
        MailTemplateSaveReqVO reqVO = new MailTemplateSaveReqVO();
        reqVO.setName("官网询盘邮件转发（租户 "
                + TenantContextHolder.getRequiredTenantId() + "）");
        reqVO.setCode(code);
        reqVO.setAccountId(config.getMailAccountId() != null
                ? config.getMailAccountId() : existing == null ? null : existing.getAccountId());
        reqVO.setNickname(config.getSenderName());
        reqVO.setTitle(config.getSubjectTemplate());
        reqVO.setContent(config.getContentTemplate());
        reqVO.setStatus(Boolean.TRUE.equals(config.getEnabled())
                ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus());
        reqVO.setRemark("由 ERP 询盘中心自动维护，请在询盘中心编辑格式");
        return reqVO;
    }

    private String templateCode() {
        return "website_inquiry_mail_t_" + TenantContextHolder.getRequiredTenantId();
    }

    private void validateEnabledConfiguration(WebsiteInquiryMailConfigSaveReqVO reqVO,
                                              MailAccountDO account) {
        if (!Validator.isEmail(reqVO.getRecipientEmail())) {
            throw exception(WEBSITE_INQUIRY_MAIL_CONFIGURATION_ERROR, "请填写有效的接收邮箱");
        }
        if (account == null) {
            throw exception(WEBSITE_INQUIRY_MAIL_CONFIGURATION_ERROR, "请选择平台发件邮箱");
        }
        if (!isValidBaseUrl(reqVO.getErpBaseUrl())) {
            throw exception(WEBSITE_INQUIRY_MAIL_CONFIGURATION_ERROR,
                    "ERP 地址必须是有效的 http 或 https 地址");
        }
    }

    private boolean isEnabledAndComplete(WebsiteInquiryMailConfigDO config) {
        return config != null && Boolean.TRUE.equals(config.getEnabled())
                && hasCompleteConfiguration(config);
    }

    private boolean hasCompleteConfiguration(WebsiteInquiryMailConfigDO config) {
        return Validator.isEmail(config.getRecipientEmail())
                && config.getMailAccountId() != null
                && config.getMailTemplateId() != null
                && StrUtil.isNotBlank(config.getSenderName())
                && StrUtil.isNotBlank(config.getSubjectTemplate())
                && StrUtil.isNotBlank(config.getContentTemplate())
                && isValidBaseUrl(config.getErpBaseUrl())
                && mailAccountService.getMailAccount(config.getMailAccountId()) != null
                && mailTemplateService.getMailTemplate(config.getMailTemplateId()) != null;
    }

    private String configRequiredReason(WebsiteInquiryMailConfigDO config) {
        if (config == null) {
            return "尚未保存询盘邮件配置";
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return "询盘邮件转发未启用";
        }
        return "接收邮箱、发件邮箱、模板或 ERP 地址配置不完整";
    }

    private CrmWebsiteInquiryRespDTO getInquiry(Long inquiryId) {
        CrmWebsiteInquiryRespDTO inquiry = crmWebsiteInquiryApi.getWebsiteInquiry(inquiryId);
        if (inquiry == null) {
            throw exception(WEBSITE_INQUIRY_MAIL_CONFIGURATION_ERROR,
                    "询盘记录不存在：" + inquiryId);
        }
        if (!Validator.isEmail(inquiry.getEmail())) {
            throw exception(WEBSITE_INQUIRY_MAIL_CONFIGURATION_ERROR,
                    "客户邮箱格式不正确");
        }
        return inquiry;
    }

    private void markFailure(Long deliveryId, Long mailLogId, RuntimeException ex) {
        markFailure(deliveryId, mailLogId,
                StrUtil.blankToDefault(ex.getMessage(), ex.getClass().getSimpleName()));
    }

    private void markFailure(Long deliveryId, Long mailLogId, String error) {
        WebsiteInquiryMailDeliveryDO delivery = deliveryMapper.selectById(deliveryId);
        int attempts = delivery == null ? 1 : Math.max(1, defaultInt(delivery.getAttemptCount()));
        LocalDateTime nextRetry = attempts >= MAX_ATTEMPTS
                ? null : LocalDateTime.now().plus(retryDelay(attempts));
        deliveryMapper.markResult(deliveryId,
                mailLogId != null ? mailLogId : delivery == null ? null : delivery.getMailLogId(),
                WebsiteInquiryMailDeliveryStatusEnum.FAILURE.getStatus(),
                null, nextRetry, truncate(error, 2000));
    }

    private static Duration retryDelay(int attempt) {
        int index = Math.min(Math.max(attempt, 1), RETRY_DELAYS.size()) - 1;
        return RETRY_DELAYS.get(index);
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static String truncate(String value, int maxLength) {
        String normalized = StrUtil.blankToDefault(value, "邮件发送失败");
        return normalized.length() <= maxLength
                ? normalized : normalized.substring(0, maxLength);
    }

    private static boolean isValidBaseUrl(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && StrUtil.isNotBlank(uri.getHost())
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private static void normalize(WebsiteInquiryMailConfigSaveReqVO reqVO) {
        reqVO.setEnabled(Boolean.TRUE.equals(reqVO.getEnabled()));
        reqVO.setRecipientEmail(StrUtil.trim(reqVO.getRecipientEmail()));
        reqVO.setSenderName(sanitizeHeader(StrUtil.trim(reqVO.getSenderName())));
        reqVO.setSubjectTemplate(StrUtil.trim(reqVO.getSubjectTemplate()));
        reqVO.setContentTemplate(StrUtil.trim(reqVO.getContentTemplate()));
        reqVO.setErpBaseUrl(StrUtil.removeSuffix(
                StrUtil.trim(reqVO.getErpBaseUrl()), "/"));
    }

    private static String sanitizeHeader(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\p{Cntrl}]+", " ").trim();
    }

    private static Map<String, Object> testLogParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("test", true);
        params.put("source", "website-inquiry-mail-settings");
        return params;
    }

}
