package cn.iocoder.yudao.module.system.dal.mysql.inquiry;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.inquiry.WebsiteInquiryMailDeliveryDO;
import cn.iocoder.yudao.module.system.enums.inquiry.WebsiteInquiryMailDeliveryStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WebsiteInquiryMailDeliveryMapper extends BaseMapperX<WebsiteInquiryMailDeliveryDO> {

    default WebsiteInquiryMailDeliveryDO selectByInquiryId(Long inquiryId) {
        return selectOne(WebsiteInquiryMailDeliveryDO::getInquiryId, inquiryId);
    }

    default List<WebsiteInquiryMailDeliveryDO> selectRetryable(
            LocalDateTime now, Integer maxAttempts, Integer limit) {
        return selectList(new LambdaQueryWrapperX<WebsiteInquiryMailDeliveryDO>()
                .in(WebsiteInquiryMailDeliveryDO::getStatus,
                        WebsiteInquiryMailDeliveryStatusEnum.PENDING.getStatus(),
                        WebsiteInquiryMailDeliveryStatusEnum.SENDING.getStatus(),
                        WebsiteInquiryMailDeliveryStatusEnum.FAILURE.getStatus(),
                        WebsiteInquiryMailDeliveryStatusEnum.CONFIG_REQUIRED.getStatus())
                .lt(WebsiteInquiryMailDeliveryDO::getAttemptCount, maxAttempts)
                .and(query -> query.isNull(WebsiteInquiryMailDeliveryDO::getNextRetryTime)
                        .or().le(WebsiteInquiryMailDeliveryDO::getNextRetryTime, now))
                .orderByAsc(WebsiteInquiryMailDeliveryDO::getNextRetryTime)
                .orderByAsc(WebsiteInquiryMailDeliveryDO::getId)
                .last("LIMIT " + limit));
    }

    default int claim(Long id, Integer maxAttempts, LocalDateTime retryDeadline) {
        return update(null, new LambdaUpdateWrapper<WebsiteInquiryMailDeliveryDO>()
                .eq(WebsiteInquiryMailDeliveryDO::getId, id)
                .in(WebsiteInquiryMailDeliveryDO::getStatus,
                        WebsiteInquiryMailDeliveryStatusEnum.PENDING.getStatus(),
                        WebsiteInquiryMailDeliveryStatusEnum.SENDING.getStatus(),
                        WebsiteInquiryMailDeliveryStatusEnum.FAILURE.getStatus(),
                        WebsiteInquiryMailDeliveryStatusEnum.CONFIG_REQUIRED.getStatus())
                .lt(WebsiteInquiryMailDeliveryDO::getAttemptCount, maxAttempts)
                .set(WebsiteInquiryMailDeliveryDO::getStatus,
                        WebsiteInquiryMailDeliveryStatusEnum.SENDING.getStatus())
                .set(WebsiteInquiryMailDeliveryDO::getNextRetryTime, retryDeadline)
                .set(WebsiteInquiryMailDeliveryDO::getLastError, "")
                .setSql("attempt_count = attempt_count + 1"));
    }

    default int markQueuedIfSending(Long id, Long mailLogId, String recipientEmail,
                                    String customerEmail) {
        return update(null, new LambdaUpdateWrapper<WebsiteInquiryMailDeliveryDO>()
                .eq(WebsiteInquiryMailDeliveryDO::getId, id)
                .eq(WebsiteInquiryMailDeliveryDO::getStatus,
                        WebsiteInquiryMailDeliveryStatusEnum.SENDING.getStatus())
                .set(WebsiteInquiryMailDeliveryDO::getMailLogId, mailLogId)
                .set(WebsiteInquiryMailDeliveryDO::getRecipientEmail, recipientEmail)
                .set(WebsiteInquiryMailDeliveryDO::getCustomerEmail, customerEmail));
    }

    default int markResult(Long id, Long mailLogId, Integer status,
                           LocalDateTime sentTime, LocalDateTime nextRetryTime, String error) {
        return update(null, new LambdaUpdateWrapper<WebsiteInquiryMailDeliveryDO>()
                .eq(WebsiteInquiryMailDeliveryDO::getId, id)
                .set(WebsiteInquiryMailDeliveryDO::getMailLogId, mailLogId)
                .set(WebsiteInquiryMailDeliveryDO::getStatus, status)
                .set(WebsiteInquiryMailDeliveryDO::getSentTime, sentTime)
                .set(WebsiteInquiryMailDeliveryDO::getNextRetryTime, nextRetryTime)
                .set(WebsiteInquiryMailDeliveryDO::getLastError, error));
    }

    default int resetForManualResend(Long id) {
        return update(null, new LambdaUpdateWrapper<WebsiteInquiryMailDeliveryDO>()
                .eq(WebsiteInquiryMailDeliveryDO::getId, id)
                .set(WebsiteInquiryMailDeliveryDO::getStatus,
                        WebsiteInquiryMailDeliveryStatusEnum.PENDING.getStatus())
                .set(WebsiteInquiryMailDeliveryDO::getAttemptCount, 0)
                .set(WebsiteInquiryMailDeliveryDO::getMailLogId, null)
                .set(WebsiteInquiryMailDeliveryDO::getNextRetryTime, LocalDateTime.now())
                .set(WebsiteInquiryMailDeliveryDO::getSentTime, null)
                .set(WebsiteInquiryMailDeliveryDO::getLastError, ""));
    }

}
