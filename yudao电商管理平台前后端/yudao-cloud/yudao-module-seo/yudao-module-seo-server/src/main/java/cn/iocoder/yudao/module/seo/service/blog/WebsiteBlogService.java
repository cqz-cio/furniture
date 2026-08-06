package cn.iocoder.yudao.module.seo.service.blog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticlePageReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticleRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticleSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogPreviewTicketRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogPublishRecordRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogSummaryRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogVersionReqVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogArticleRespVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogPageRespVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogPreviewSessionRespVO;
import jakarta.validation.Valid;

import java.util.List;

public interface WebsiteBlogService {

    PageResult<WebsiteBlogArticleRespVO> getArticlePage(@Valid WebsiteBlogArticlePageReqVO reqVO);

    WebsiteBlogSummaryRespVO getSummary(Long siteId, String locale);

    WebsiteBlogArticleRespVO getArticle(Long id);

    Long createArticle(@Valid WebsiteBlogArticleSaveReqVO reqVO);

    void updateArticle(@Valid WebsiteBlogArticleSaveReqVO reqVO);

    void deleteArticle(Long id);

    void publishArticle(@Valid WebsiteBlogVersionReqVO reqVO);

    void offlineArticle(@Valid WebsiteBlogVersionReqVO reqVO);

    List<WebsiteBlogPublishRecordRespVO> getPublishHistory(Long articleId);

    WebsiteBlogPreviewTicketRespVO createPreviewTicket(@Valid WebsiteBlogVersionReqVO reqVO);

    AppWebsiteBlogPageRespVO getPublishedPage(
            Long siteId, String locale, Integer page, Integer pageSize);

    AppWebsiteBlogArticleRespVO getPublishedArticle(Long siteId, String locale, String slug);

    AppWebsiteBlogPreviewSessionRespVO exchangePreviewTicket(String ticket, String requestOrigin);

    AppWebsiteBlogArticleRespVO getPreview(String session, String requestOrigin);

}
