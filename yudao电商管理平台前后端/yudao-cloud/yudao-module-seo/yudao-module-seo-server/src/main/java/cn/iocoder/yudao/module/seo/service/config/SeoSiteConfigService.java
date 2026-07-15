package cn.iocoder.yudao.module.seo.service.config;

import cn.iocoder.yudao.module.seo.controller.admin.config.vo.SeoSiteConfigSaveReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import jakarta.validation.Valid;

public interface SeoSiteConfigService {

    void saveSiteConfig(@Valid SeoSiteConfigSaveReqVO reqVO);

    SeoSiteConfigDO getSiteConfig(Long siteId);

    SeoSiteConfigDO getRequiredSiteConfig(Long siteId);

}
