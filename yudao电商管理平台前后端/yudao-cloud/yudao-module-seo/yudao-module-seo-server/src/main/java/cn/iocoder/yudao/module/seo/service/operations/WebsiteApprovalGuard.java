package cn.iocoder.yudao.module.seo.service.operations;

import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.operations.WebsiteOperationRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.OPERATION_APPROVAL_REQUIRED;

/** Enforced by the content service, so direct ERP API calls cannot bypass approval. */
@Service
public class WebsiteApprovalGuard {
    @Resource private SeoSiteConfigMapper sites;
    @Resource private WebsiteOperationRecordMapper records;
    public void requireApproved(Long siteId,String kind,String target,Integer version) {
        var site=sites.selectBySiteId(siteId);
        if(site==null || !"TABLEWARE_B2B".equals(site.getNavigationTemplate()))return;
        if(records.approval(siteId,kind,target,version)==null)throw exception(OPERATION_APPROVAL_REQUIRED);
    }
    public void published(Long siteId,String kind,String target,Integer version) {
        var site=sites.selectBySiteId(siteId);
        if(site==null || !"TABLEWARE_B2B".equals(site.getNavigationTemplate()))return;
        var approved=records.approval(siteId,kind,target,version);
        if(approved!=null){approved.setStatus("published");approved.setVersion(approved.getVersion()+1);records.updateById(approved);}
    }
}
