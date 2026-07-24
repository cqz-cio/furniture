package cn.iocoder.yudao.module.system.service.permission;

/**
 * 家具后台导航与租户权限同步 Service。
 */
public interface FurnitureNavigationPermissionService {

    /**
     * 按家具导航目录同步目标租户的套餐和角色权限。
     */
    void syncMenuPermissions();

}
