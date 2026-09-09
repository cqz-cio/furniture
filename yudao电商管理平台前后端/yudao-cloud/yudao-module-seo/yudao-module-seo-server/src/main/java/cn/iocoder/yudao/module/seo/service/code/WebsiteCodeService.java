package cn.iocoder.yudao.module.seo.service.code;

import cn.iocoder.yudao.module.seo.controller.admin.code.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface WebsiteCodeService {
    WebsiteCodeRespVO getDraft(@NotNull @Min(1) Long siteId);
    WebsiteCodeRespVO saveDraft(@Valid WebsiteCodeSaveReqVO request);
    WebsiteCodeRespVO publish(@Valid WebsiteCodeVersionReqVO request);
    WebsiteCodeRespVO restoreDraft(@Valid WebsiteCodeRestoreReqVO request);
    List<WebsiteCodeHistoryRespVO> getHistory(@NotNull @Min(1) Long siteId);
    WebsiteCodeRespVO getPublished(@NotNull @Min(1) Long siteId);
}
