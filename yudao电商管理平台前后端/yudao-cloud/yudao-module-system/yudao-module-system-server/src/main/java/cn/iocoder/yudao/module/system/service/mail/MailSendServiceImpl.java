package cn.iocoder.yudao.module.system.service.mail;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailAccountDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailTemplateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.mq.message.mail.MailSendMessage;
import cn.iocoder.yudao.module.system.mq.producer.mail.MailProducer;
import cn.iocoder.yudao.module.system.service.member.MemberService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hutool.extra.mail.MailAccount;
import org.dromara.hutool.extra.mail.Mail;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

/**
 * 邮箱发送 Service 实现类
 *
 * @author wangjingyi
 * @since 2022-03-21
 */
@Service
@Validated
@Slf4j
public class MailSendServiceImpl implements MailSendService {

    @Resource
    private AdminUserService adminUserService;
    @Resource
    private MemberService memberService;

    @Resource
    private MailAccountService mailAccountService;
    @Resource
    private MailTemplateService mailTemplateService;

    @Resource
    private MailLogService mailLogService;
    @Resource
    private MailProducer mailProducer;

    @Override
    public Long sendSingleMail(Collection<String> toMails, Collection<String> ccMails, Collection<String> bccMails,
                               Long userId, Integer userType,
                               String templateCode, Map<String, Object> templateParams,
                               File... attachments) {
        // 1.1 校验邮箱模版是否合法
        MailTemplateDO template = validateMailTemplate(templateCode);
        // 1.2 校验邮箱账号是否合法
        MailAccountDO account = validateMailAccount(template.getAccountId());
        // 1.3 校验邮件参数是否缺失
        validateTemplateParams(template, templateParams);

        // 2. 组装邮箱
        String userMail = getUserMail(userId, userType);
        Collection<String> toMailSet = new LinkedHashSet<>();
        Collection<String> ccMailSet = new LinkedHashSet<>();
        Collection<String> bccMailSet = new LinkedHashSet<>();
        if (Validator.isEmail(userMail)) {
            toMailSet.add(userMail);
        }
        if (CollUtil.isNotEmpty(toMails)) {
            toMails.stream().filter(Validator::isEmail).forEach(toMailSet::add);
        }
        if (CollUtil.isNotEmpty(ccMails)) {
            ccMails.stream().filter(Validator::isEmail).forEach(ccMailSet::add);
        }
        if (CollUtil.isNotEmpty(bccMails)) {
            bccMails.stream().filter(Validator::isEmail).forEach(bccMailSet::add);
        }
        if (CollUtil.isEmpty(toMailSet)) {
            throw exception(MAIL_SEND_MAIL_NOT_EXISTS);
        }

        // 创建发送日志。如果模板被禁用，则不发送邮件，只记录日志
        Boolean isSend = CommonStatusEnum.ENABLE.getStatus().equals(template.getStatus());
        String title = mailTemplateService.formatMailTemplateContent(template.getTitle(), templateParams);
        String content = mailTemplateService.formatMailTemplateContent(template.getContent(), templateParams);
        Long sendLogId = createLogAndPublish(userId, userType, toMailSet, ccMailSet, bccMailSet, null,
                account, template, title, content, templateParams, isSend, false, null, attachments);
        return sendLogId;
    }

    @Override
    public Long sendPreparedMail(Collection<String> toMails, Collection<String> ccMails,
                                 Collection<String> bccMails, Collection<String> replyToMails,
                                 Long userId, Integer userType,
                                 String templateCode, Map<String, Object> logParams,
                                 String title, String htmlContent, Long websiteInquiryDeliveryId,
                                 File... attachments) {
        MailTemplateDO template = validateMailTemplate(templateCode);
        MailAccountDO account = validateMailAccount(template.getAccountId());
        if (StrUtil.isBlank(title) || StrUtil.isBlank(htmlContent)) {
            throw exception(MAIL_SEND_TEMPLATE_PARAM_MISS, "renderedContent");
        }

        Collection<String> toMailSet = collectValidMails(getUserMail(userId, userType), toMails);
        Collection<String> ccMailSet = collectValidMails(null, ccMails);
        Collection<String> bccMailSet = collectValidMails(null, bccMails);
        Collection<String> replyToMailSet = collectValidMails(null, replyToMails);
        if (CollUtil.isEmpty(toMailSet)) {
            throw exception(MAIL_SEND_MAIL_NOT_EXISTS);
        }

        Boolean isSend = CommonStatusEnum.ENABLE.getStatus().equals(template.getStatus());
        return createLogAndPublish(userId, userType, toMailSet, ccMailSet, bccMailSet, replyToMailSet,
                account, template, title, htmlContent, logParams, isSend,
                true, websiteInquiryDeliveryId, attachments);
    }

    private Long createLogAndPublish(Long userId, Integer userType,
                                     Collection<String> toMails, Collection<String> ccMails,
                                     Collection<String> bccMails, Collection<String> replyToMails,
                                     MailAccountDO account, MailTemplateDO template,
                                     String title, String content, Map<String, Object> logParams,
                                     Boolean isSend, boolean prepared,
                                     Long websiteInquiryDeliveryId, File[] attachments) {
        Long sendLogId = prepared
                ? mailLogService.createPreparedMailLog(userId, userType, toMails, ccMails, bccMails,
                        account, template, title, content, logParams, isSend)
                : mailLogService.createMailLog(userId, userType, toMails, ccMails, bccMails,
                        account, template, content, logParams, isSend);
        // 发送 Spring Event，异步执行邮件发送
        if (isSend) {
            if (CollUtil.isEmpty(replyToMails) && websiteInquiryDeliveryId == null) {
                mailProducer.sendMailSendMessage(sendLogId, toMails, ccMails, bccMails,
                        account.getId(), template.getNickname(), title, content, attachments);
            } else {
                mailProducer.sendMailSendMessage(sendLogId, toMails, ccMails, bccMails, replyToMails,
                        account.getId(), template.getNickname(), title, content, attachments,
                        websiteInquiryDeliveryId);
            }
        }
        return sendLogId;
    }

    private Collection<String> collectValidMails(String firstMail, Collection<String> mails) {
        Collection<String> result = new LinkedHashSet<>();
        if (Validator.isEmail(firstMail)) {
            result.add(firstMail);
        }
        if (CollUtil.isNotEmpty(mails)) {
            mails.stream().filter(Validator::isEmail).forEach(result::add);
        }
        return result;
    }

    private String getUserMail(Long userId, Integer userType) {
        if (userId == null || userType == null) {
            return null;
        }
        if (UserTypeEnum.ADMIN.getValue().equals(userType)) {
            AdminUserDO user = adminUserService.getUser(userId);
            if (user != null) {
                return user.getEmail();
            }
        }
        if (UserTypeEnum.MEMBER.getValue().equals(userType)) {
            return memberService.getMemberUserEmail(userId);
        }
        return null;
    }

    @Override
    public void doSendMail(MailSendMessage message) {
        try {
            // 1. 创建发送账号
            MailAccountDO account = validateMailAccount(message.getAccountId());
            MailAccount mailAccount = buildMailAccount(account, message.getNickname());
            // 2. 发送邮件
            Mail mail = Mail.of(mailAccount)
                    .setTos(message.getToMails().toArray(String[]::new))
                    .setTitle(message.getTitle())
                    .setContent(message.getContent(), true);
            if (CollUtil.isNotEmpty(message.getCcMails())) {
                mail.setCcs(message.getCcMails().toArray(String[]::new));
            }
            if (CollUtil.isNotEmpty(message.getBccMails())) {
                mail.setBccs(message.getBccMails().toArray(String[]::new));
            }
            if (CollUtil.isNotEmpty(message.getReplyToMails())) {
                mail.setReply(message.getReplyToMails().toArray(String[]::new));
            }
            if (message.getAttachments() != null && message.getAttachments().length > 0) {
                mail.setFiles(message.getAttachments());
            }
            String messageId = mail.send();
            // 3. 更新结果（成功）
            mailLogService.updateMailSendResult(message.getLogId(), messageId, null);
        } catch (Exception e) {
            // 3. 更新结果（异常）
            mailLogService.updateMailSendResult(message.getLogId(), null, e);
        }
    }

    private MailAccount buildMailAccount(MailAccountDO account, String nickname) {
        String from = StrUtil.isNotEmpty(nickname) ? nickname + " <" + account.getMail() + ">" : account.getMail();
        MailAccount mailAccount = new MailAccount().setFrom(from).setAuth(true)
                .setUser(account.getUsername()).setPass(account.getPassword().toCharArray())
                .setHost(account.getHost()).setPort(account.getPort())
                .setSslEnable(account.getSslEnable()).setStarttlsEnable(account.getStarttlsEnable());
        if (Boolean.TRUE.equals(account.getSslEnable()) && StrUtil.isNotBlank(account.getHost())) {
            mailAccount.setCustomProperty("mail.smtp.ssl.trust", account.getHost());
        }
        return mailAccount;
    }

    @VisibleForTesting
    MailTemplateDO validateMailTemplate(String templateCode) {
        // 获得邮件模板。考虑到效率，从缓存中获取
        MailTemplateDO template = mailTemplateService.getMailTemplateByCodeFromCache(templateCode);
        // 邮件模板不存在
        if (template == null) {
            throw exception(MAIL_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    @VisibleForTesting
    MailAccountDO validateMailAccount(Long accountId) {
        // 获得邮箱账号。考虑到效率，从缓存中获取
        MailAccountDO account = mailAccountService.getMailAccountFromCache(accountId);
        // 邮箱账号不存在
        if (account == null) {
            throw exception(MAIL_ACCOUNT_NOT_EXISTS);
        }
        return account;
    }

    /**
     * 校验邮件参数是否缺失
     *
     * @param template 邮箱模板
     * @param templateParams 参数列表
     */
    @VisibleForTesting
    void validateTemplateParams(MailTemplateDO template, Map<String, Object> templateParams) {
        template.getParams().forEach(key -> {
            Object value = templateParams.get(key);
            if (value == null) {
                throw exception(MAIL_SEND_TEMPLATE_PARAM_MISS, key);
            }
        });
    }

}
