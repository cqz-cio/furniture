package cn.iocoder.yudao.module.member.dal.mysql.giftregistry;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.controller.admin.giftregistry.vo.MemberGiftRegistryPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.giftregistry.vo.AppGiftRegistrySearchReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryDO;
import cn.iocoder.yudao.module.member.service.giftregistry.MemberGiftRegistryService;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberGiftRegistryMapper extends BaseMapperX<MemberGiftRegistryDO> {

    default MemberGiftRegistryDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<MemberGiftRegistryDO>()
                .eq(MemberGiftRegistryDO::getUserId, userId)
                .orderByDesc(MemberGiftRegistryDO::getId)
                .last("LIMIT 1"));
    }

    default MemberGiftRegistryDO selectPublicByPublicCode(String publicCode) {
        return selectOne(new LambdaQueryWrapperX<MemberGiftRegistryDO>()
                .eq(MemberGiftRegistryDO::getPublicCode, publicCode)
                .eq(MemberGiftRegistryDO::getVisibility, MemberGiftRegistryService.VISIBILITY_PUBLIC)
                .eq(MemberGiftRegistryDO::getStatus, MemberGiftRegistryService.STATUS_ACTIVE)
                .last("LIMIT 1"));
    }

    default PageResult<MemberGiftRegistryDO> selectPublicPage(AppGiftRegistrySearchReqVO reqVO) {
        LambdaQueryWrapperX<MemberGiftRegistryDO> wrapper = new LambdaQueryWrapperX<MemberGiftRegistryDO>()
                .eq(MemberGiftRegistryDO::getVisibility, MemberGiftRegistryService.VISIBILITY_PUBLIC)
                .eq(MemberGiftRegistryDO::getStatus, MemberGiftRegistryService.STATUS_ACTIVE)
                .betweenIfPresent(MemberGiftRegistryDO::getEventDate, reqVO.getEventStart(), reqVO.getEventEnd())
                .orderByDesc(MemberGiftRegistryDO::getId);
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            wrapper.and(query -> query
                    .like(MemberGiftRegistryDO::getRegistrantName, reqVO.getKeyword())
                    .or()
                    .like(MemberGiftRegistryDO::getCoRegistrantName, reqVO.getKeyword())
                    .or()
                    .like(MemberGiftRegistryDO::getEmail, reqVO.getKeyword()));
        }
        return selectPage(reqVO, wrapper);
    }

    default PageResult<MemberGiftRegistryDO> selectPage(MemberGiftRegistryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MemberGiftRegistryDO>()
                .eqIfPresent(MemberGiftRegistryDO::getUserId, reqVO.getUserId())
                .eqIfPresent(MemberGiftRegistryDO::getStatus, reqVO.getStatus())
                .eqIfPresent(MemberGiftRegistryDO::getEventType, reqVO.getEventType())
                .likeIfPresent(MemberGiftRegistryDO::getRegistrantName, reqVO.getRegistrantName())
                .likeIfPresent(MemberGiftRegistryDO::getPublicCode, reqVO.getPublicCode())
                .orderByDesc(MemberGiftRegistryDO::getId));
    }

}
