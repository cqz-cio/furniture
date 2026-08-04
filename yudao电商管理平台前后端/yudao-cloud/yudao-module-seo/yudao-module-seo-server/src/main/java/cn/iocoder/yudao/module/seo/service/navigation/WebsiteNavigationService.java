package cn.iocoder.yudao.module.seo.service.navigation;

import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationCategoryOptionRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationDraftRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationDraftSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPreviewTicketReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPreviewTicketRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPublishReqVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationPreviewSessionRespVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationRespVO;
import jakarta.validation.Valid;

import java.util.List;

public interface WebsiteNavigationService {

    WebsiteNavigationDraftRespVO getDraft(Long siteId, String locale);

    List<WebsiteNavigationCategoryOptionRespVO> getCategoryOptions(Long siteId, String locale);

    void saveDraft(@Valid WebsiteNavigationDraftSaveReqVO reqVO);

    void publish(@Valid WebsiteNavigationPublishReqVO reqVO);

    AppWebsiteNavigationRespVO getPublished(Long siteId, String locale);

    WebsiteNavigationPreviewTicketRespVO createPreviewTicket(
            @Valid WebsiteNavigationPreviewTicketReqVO reqVO);

    AppWebsiteNavigationPreviewSessionRespVO exchangePreviewTicket(String ticket, String requestOrigin);

    AppWebsiteNavigationRespVO getPreview(String session, String requestOrigin);

}
