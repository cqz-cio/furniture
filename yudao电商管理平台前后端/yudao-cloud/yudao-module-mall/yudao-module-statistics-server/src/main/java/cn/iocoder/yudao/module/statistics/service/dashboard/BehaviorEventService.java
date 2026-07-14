package cn.iocoder.yudao.module.statistics.service.dashboard;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppBehaviorEventTrackReqVO;
public interface BehaviorEventService { void trackPublic(AppBehaviorEventTrackReqVO request, String rawVisitorId, String rawSessionId, String consentEvidence, String clientIp, Long userId); void recordTrusted(TrustedBehaviorEventCommand command); }
