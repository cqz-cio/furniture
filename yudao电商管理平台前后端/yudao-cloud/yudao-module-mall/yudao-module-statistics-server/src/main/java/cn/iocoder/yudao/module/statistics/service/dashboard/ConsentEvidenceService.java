package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppConsentEvidenceIssueReqVO;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppConsentEvidenceRespVO;

public interface ConsentEvidenceService {
    AppConsentEvidenceRespVO issue(AppConsentEvidenceIssueReqVO request, String clientIp);
    void withdraw(String evidence);
}
