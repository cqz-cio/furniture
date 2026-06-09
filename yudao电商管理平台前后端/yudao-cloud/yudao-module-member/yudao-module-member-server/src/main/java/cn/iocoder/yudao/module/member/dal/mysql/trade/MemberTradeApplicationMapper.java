package cn.iocoder.yudao.module.member.dal.mysql.trade;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.trade.MemberTradeApplicationDO;
import cn.iocoder.yudao.module.member.enums.trade.MemberTradeApplicationStatusEnum;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberTradeApplicationMapper extends BaseMapperX<MemberTradeApplicationDO> {

    default MemberTradeApplicationDO selectPendingByPrimaryEmail(String primaryEmail) {
        return selectOne(new LambdaQueryWrapperX<MemberTradeApplicationDO>()
                .eq(MemberTradeApplicationDO::getPrimaryEmail, primaryEmail)
                .eq(MemberTradeApplicationDO::getStatus, MemberTradeApplicationStatusEnum.PENDING.getStatus()));
    }

    default PageResult<MemberTradeApplicationDO> selectPage(MemberTradeApplicationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MemberTradeApplicationDO>()
                .eqIfPresent(MemberTradeApplicationDO::getStatus, reqVO.getStatus())
                .likeIfPresent(MemberTradeApplicationDO::getPrimaryEmail, reqVO.getPrimaryEmail())
                .likeIfPresent(MemberTradeApplicationDO::getBusinessName, reqVO.getBusinessName())
                .orderByDesc(MemberTradeApplicationDO::getId));
    }

}
