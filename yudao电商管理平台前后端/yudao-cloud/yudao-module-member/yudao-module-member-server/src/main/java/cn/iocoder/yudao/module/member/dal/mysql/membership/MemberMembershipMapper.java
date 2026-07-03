package cn.iocoder.yudao.module.member.dal.mysql.membership;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.controller.admin.membership.vo.MemberMembershipPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.membership.MemberMembershipDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberMembershipMapper extends BaseMapperX<MemberMembershipDO> {

    default MemberMembershipDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<MemberMembershipDO>()
                .eq(MemberMembershipDO::getUserId, userId)
                .orderByDesc(MemberMembershipDO::getId)
                .last("LIMIT 1"));
    }

    default PageResult<MemberMembershipDO> selectPage(MemberMembershipPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MemberMembershipDO>()
                .eqIfPresent(MemberMembershipDO::getUserId, reqVO.getUserId())
                .eqIfPresent(MemberMembershipDO::getStatus, reqVO.getStatus())
                .eqIfPresent(MemberMembershipDO::getPlanCode, reqVO.getPlanCode())
                .likeIfPresent(MemberMembershipDO::getMemberId, reqVO.getMemberId())
                .orderByDesc(MemberMembershipDO::getId));
    }

}
