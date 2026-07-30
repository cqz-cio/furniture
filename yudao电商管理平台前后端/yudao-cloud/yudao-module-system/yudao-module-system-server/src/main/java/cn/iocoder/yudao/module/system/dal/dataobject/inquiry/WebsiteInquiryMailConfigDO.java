package cn.iocoder.yudao.module.system.dal.dataobject.inquiry;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 官网询盘邮件转发配置。
 */
@TableName("system_website_inquiry_mail_config")
@KeySequence("system_website_inquiry_mail_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebsiteInquiryMailConfigDO extends BaseDO {

    @TableId
    private Long id;
    private Boolean enabled;
    private String recipientEmail;
    private Long mailAccountId;
    private Long mailTemplateId;
    private String senderName;
    private String subjectTemplate;
    private String contentTemplate;
    private String erpBaseUrl;

}
