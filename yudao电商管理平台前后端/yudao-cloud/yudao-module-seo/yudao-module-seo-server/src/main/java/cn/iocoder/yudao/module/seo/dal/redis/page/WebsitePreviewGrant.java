package cn.iocoder.yudao.module.seo.dal.redis.page;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.WebsitePageRespVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationRespVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogArticleRespVO;
public record WebsitePreviewGrant(Long tenantId, Long siteId, String origin, String locale,
    WebsitePageRespVO page, AppWebsiteNavigationRespVO navigation, AppWebsiteBlogArticleRespVO article) {}
