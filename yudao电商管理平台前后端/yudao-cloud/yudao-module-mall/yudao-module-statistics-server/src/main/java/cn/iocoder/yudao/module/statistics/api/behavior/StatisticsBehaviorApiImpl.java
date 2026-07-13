package cn.iocoder.yudao.module.statistics.api.behavior;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.statistics.api.behavior.dto.CartBehaviorRecordReqDTO;
import cn.iocoder.yudao.module.statistics.service.dashboard.*;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import javax.annotation.Resource;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @Validated
public class StatisticsBehaviorApiImpl implements StatisticsBehaviorApi {
 @Resource private BehaviorEventService service;
 @Override @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(sanitizeKeys={"visitorId","sessionId","consentEvidence"}) public CommonResult<Boolean> recordCartAdded(CartBehaviorRecordReqDTO r){ service.recordTrusted(new TrustedBehaviorEventCommand().setEventId(r.getEventId()).setUserId(r.getUserId()).setSpuId(r.getSpuId()).setSkuId(r.getSkuId()).setQuantity(r.getQuantity()).setRawVisitorId(r.getVisitorId()).setRawSessionId(r.getSessionId()).setConsentEvidence(r.getConsentEvidence())); return success(true); }
}
