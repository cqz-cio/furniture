package cn.iocoder.yudao.module.seo.service.metadata;

import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataSaveReqVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataPageReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.metadata.SeoMetadataDO;
import jakarta.validation.Valid;

public interface SeoMetadataService {

    Long createMetadata(@Valid SeoMetadataSaveReqVO reqVO);

    void updateMetadata(@Valid SeoMetadataSaveReqVO reqVO);

    void publishMetadata(Long id, Integer version);

    void deleteMetadata(Long id);

    SeoMetadataDO getMetadata(Long id);

    PageResult<SeoMetadataDO> getMetadataPage(SeoMetadataPageReqVO reqVO);

    SeoMetadataDO getPublishedMetadata(Long siteId, String entityType, Long entityId, String locale);

}
