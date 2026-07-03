package cn.iocoder.yudao.module.member.dal.mysql.giftregistry;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MemberGiftRegistryItemMapper extends BaseMapperX<MemberGiftRegistryItemDO> {

    default List<MemberGiftRegistryItemDO> selectListByRegistryId(Long registryId) {
        return selectList(new LambdaQueryWrapperX<MemberGiftRegistryItemDO>()
                .eq(MemberGiftRegistryItemDO::getRegistryId, registryId)
                .orderByDesc(MemberGiftRegistryItemDO::getId));
    }

    default MemberGiftRegistryItemDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<MemberGiftRegistryItemDO>()
                .eq(MemberGiftRegistryItemDO::getId, id)
                .eq(MemberGiftRegistryItemDO::getUserId, userId)
                .last("LIMIT 1"));
    }

}
