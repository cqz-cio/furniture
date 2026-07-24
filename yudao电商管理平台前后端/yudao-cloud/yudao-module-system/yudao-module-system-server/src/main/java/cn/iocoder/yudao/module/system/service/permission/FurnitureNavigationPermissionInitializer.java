package cn.iocoder.yudao.module.system.service.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 服务启动后自动校准家具导航权限。
 */
@Component
@RequiredArgsConstructor
public class FurnitureNavigationPermissionInitializer implements ApplicationRunner {

    private final FurnitureNavigationPermissionService furnitureNavigationPermissionService;

    @Override
    public void run(ApplicationArguments args) {
        furnitureNavigationPermissionService.syncMenuPermissions();
    }

}
