package cn.iocoder.yudao.module.seo.controller.metadata;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.SeoMetadataController;
import cn.iocoder.yudao.module.seo.controller.app.metadata.AppSeoMetadataController;
import cn.iocoder.yudao.module.seo.controller.app.metadata.vo.SeoPublicMetadataRespVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.metadata.SeoMetadataDO;
import cn.iocoder.yudao.module.seo.service.metadata.SeoMetadataService;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeoMetadataControllerContractTest {

    @Test
    void appResolve_shouldBePermitAllTenantScopedAndUsePublicPath() throws Exception {
        RequestMapping mapping = AppSeoMetadataController.class.getAnnotation(RequestMapping.class);
        Method resolve = AppSeoMetadataController.class.getMethod(
                "resolve", Long.class, String.class, Long.class, String.class);

        assertThat(mapping.value()).containsExactly("/seo/metadata");
        assertThat(resolve.getAnnotation(PermitAll.class)).isNotNull();
        assertThat(AppSeoMetadataController.class.getAnnotation(TenantIgnore.class)).isNull();
        assertThat(resolve.getAnnotation(TenantIgnore.class)).isNull();
    }

    @Test
    void publicResponse_shouldExposeOnlyStablePublicFieldsAndMapRenamedFields() {
        Set<String> publicFields = Arrays.stream(SeoPublicMetadataRespVO.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        assertThat(publicFields).containsExactlyInAnyOrder(
                "title", "description", "canonicalUrl", "robotsIndex", "robotsFollow",
                "ogTitle", "ogDescription", "ogImage", "schemaType", "locale", "version");

        SeoMetadataService service = mock(SeoMetadataService.class);
        SeoMetadataDO metadata = new SeoMetadataDO()
                .setId(99L)
                .setSeoTitle("Public title")
                .setMetaDescription("Public description")
                .setFocusKeyphrase("secret focus")
                .setCanonicalUrl("https://shop.example.com/item")
                .setRobotsIndex(true)
                .setRobotsFollow(false)
                .setOgTitle("OG title")
                .setOgDescription("OG description")
                .setOgImage("https://cdn.example.com/og.jpg")
                .setSchemaType("Product")
                .setLocale("en-US")
                .setVersion(3)
                .setPublishStatus("PUBLISHED");
        when(service.getPublishedMetadata(1L, "PRODUCT", 2L, "en-US")).thenReturn(metadata);
        AppSeoMetadataController controller = new AppSeoMetadataController();
        ReflectionTestUtils.setField(controller, "metadataService", service);

        CommonResult<SeoPublicMetadataRespVO> result = controller.resolve(1L, "PRODUCT", 2L, "en-US");

        assertThat(result.getData().getTitle()).isEqualTo("Public title");
        assertThat(result.getData().getDescription()).isEqualTo("Public description");
        assertThat(result.getData().getVersion()).isEqualTo(3);
    }

    @Test
    void appResolve_shouldReturnNullDataWhenNoPublishedMatchExists() {
        SeoMetadataService service = mock(SeoMetadataService.class);
        when(service.getPublishedMetadata(1L, "PRODUCT", 2L, "en-US")).thenReturn(null);
        AppSeoMetadataController controller = new AppSeoMetadataController();
        ReflectionTestUtils.setField(controller, "metadataService", service);

        assertThat(controller.resolve(1L, "PRODUCT", 2L, "en-US").getData()).isNull();
    }

    @Test
    void adminController_shouldExposeExactMappingsAndPermissions() throws Exception {
        assertThat(SeoMetadataController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/seo/metadata");
        assertEndpoint(SeoMetadataController.class.getMethod("getMetadataPage",
                        cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataPageReqVO.class),
                GetMapping.class, "/page", "@ss.hasPermission('seo:metadata:query')");
        assertEndpoint(SeoMetadataController.class.getMethod("getMetadata", Long.class),
                GetMapping.class, "/get", "@ss.hasPermission('seo:metadata:query')");
        assertEndpoint(SeoMetadataController.class.getMethod("createMetadata",
                        cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataSaveReqVO.class),
                PostMapping.class, "/create", "@ss.hasPermission('seo:metadata:create')");
        assertEndpoint(SeoMetadataController.class.getMethod("updateMetadata",
                        cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataSaveReqVO.class),
                PutMapping.class, "/update", "@ss.hasPermission('seo:metadata:update')");
        assertEndpoint(SeoMetadataController.class.getMethod("deleteMetadata", Long.class),
                DeleteMapping.class, "/delete", "@ss.hasPermission('seo:metadata:delete')");
        assertEndpoint(SeoMetadataController.class.getMethod("publishMetadata", Long.class, Integer.class),
                PutMapping.class, "/publish", "@ss.hasPermission('seo:metadata:publish')");
    }

    private static void assertEndpoint(Method method, Class<? extends Annotation> mappingType,
                                       String path, String permission) throws Exception {
        Annotation mapping = method.getAnnotation(mappingType);
        assertThat(mapping).as("%s mapping on %s", mappingType.getSimpleName(), method.getName()).isNotNull();
        String[] paths = (String[]) mapping.annotationType().getMethod("value").invoke(mapping);
        assertThat(paths).as("mapping path for %s", method.getName()).containsExactly(path);
        assertThat(method.getAnnotation(PreAuthorize.class))
                .as("PreAuthorize on %s", method.getName())
                .isNotNull()
                .extracting(PreAuthorize::value)
                .isEqualTo(permission);
    }

}
