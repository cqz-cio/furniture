package cn.iocoder.yudao.module.member.dal.mysql.auth;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.auth.MemberEmailAuthDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberEmailAuthMapper extends BaseMapperX<MemberEmailAuthDO> {

    default MemberEmailAuthDO selectLastByEmail(String email, Integer scene, Integer credentialType) {
        return selectOne(new LambdaQueryWrapperX<MemberEmailAuthDO>()
                .eq(MemberEmailAuthDO::getEmail, email)
                .eq(MemberEmailAuthDO::getScene, scene)
                .eq(MemberEmailAuthDO::getCredentialType, credentialType)
                .orderByDesc(MemberEmailAuthDO::getId)
                .last("LIMIT 1"));
    }

    default MemberEmailAuthDO selectByCredentialHash(String credentialHash, Integer scene, Integer credentialType) {
        return selectOne(new LambdaQueryWrapperX<MemberEmailAuthDO>()
                .eq(MemberEmailAuthDO::getCredentialHash, credentialHash)
                .eq(MemberEmailAuthDO::getScene, scene)
                .eq(MemberEmailAuthDO::getCredentialType, credentialType));
    }

}
