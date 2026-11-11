package cn.iocoder.yudao.module.seo.dal.mysql.operations;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.operations.WebsiteOperationRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface WebsiteOperationRecordMapper extends BaseMapperX<WebsiteOperationRecordDO> {
    default LambdaQueryWrapper<WebsiteOperationRecordDO> scope(Long siteId) {
        return new LambdaQueryWrapper<WebsiteOperationRecordDO>().eq(WebsiteOperationRecordDO::getTenantId,TenantContextHolder.getRequiredTenantId()).eq(WebsiteOperationRecordDO::getSiteId,siteId);
    }
    default List<WebsiteOperationRecordDO> records(Long siteId,String kind) {
        return selectList(scope(siteId).eq(WebsiteOperationRecordDO::getKind,kind).orderByDesc(WebsiteOperationRecordDO::getId).last("LIMIT 300"));
    }
    default WebsiteOperationRecordDO approval(Long siteId,String kind,String target,Integer version) {
        return selectOne(scope(siteId).eq(WebsiteOperationRecordDO::getKind,"review").eq(WebsiteOperationRecordDO::getTargetKind,kind).eq(WebsiteOperationRecordDO::getTargetKey,target).eq(WebsiteOperationRecordDO::getContentVersion,version).eq(WebsiteOperationRecordDO::getStatus,"approved").last("LIMIT 1"));
    }
    default WebsiteOperationRecordDO locked(Long siteId,Long id) {
        return selectOne(scope(siteId).eq(WebsiteOperationRecordDO::getId,id).last("FOR UPDATE"));
    }
}
