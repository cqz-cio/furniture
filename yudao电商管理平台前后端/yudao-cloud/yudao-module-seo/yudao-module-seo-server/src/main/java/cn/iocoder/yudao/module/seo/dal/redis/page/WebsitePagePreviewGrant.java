package cn.iocoder.yudao.module.seo.dal.redis.page;

import cn.iocoder.yudao.module.seo.controller.admin.page.vo.WebsitePageRespVO;

public record WebsitePagePreviewGrant(Long tenantId, Long siteId, String origin, WebsitePageRespVO page) {}
