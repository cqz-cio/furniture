package cn.iocoder.yudao.module.system.dal.mysql.inquiry;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.inquiry.WebsiteInquiryMailConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebsiteInquiryMailConfigMapper extends BaseMapperX<WebsiteInquiryMailConfigDO> {

    default WebsiteInquiryMailConfigDO selectCurrentTenantConfig() {
        return selectOne(new LambdaQueryWrapperX<WebsiteInquiryMailConfigDO>()
                .orderByDesc(WebsiteInquiryMailConfigDO::getId)
                .last("LIMIT 1"));
    }

}
