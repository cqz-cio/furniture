package cn.iocoder.yudao.module.seo.dal.mysql.config;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SeoSiteConfigMapper extends BaseMapperX<SeoSiteConfigDO> {

    default SeoSiteConfigDO selectBySiteId(Long siteId) {
        return selectOne(SeoSiteConfigDO::getSiteId, siteId);
    }

    default SeoSiteConfigDO selectBySiteIdForUpdate(Long siteId) {
        return selectOne(new LambdaQueryWrapper<SeoSiteConfigDO>()
                .eq(SeoSiteConfigDO::getSiteId, siteId)
                .last("FOR UPDATE"));
    }

}
