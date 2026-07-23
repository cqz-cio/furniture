package cn.iocoder.yudao.module.seo.controller.analysis;

import cn.iocoder.yudao.module.seo.controller.admin.analysis.SeoAnalysisController;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoDocumentAnalysisRunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRerunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRunReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SeoAnalysisControllerContractTest {

    @Test
    void shouldExposeExactMappingsAndPermissions() throws Exception {
        assertThat(SeoAnalysisController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/seo/analysis");

        assertEndpoint(SeoAnalysisController.class.getMethod("runAnalysis", SeoAnalysisRunReqVO.class),
                PostMapping.class, "/run", "@ss.hasPermission('seo:analysis:run')");
        assertEndpoint(SeoAnalysisController.class.getMethod("parseDocument", MultipartFile.class),
                PostMapping.class, "/document/parse", "@ss.hasPermission('seo:analysis:run')");
        assertEndpoint(SeoAnalysisController.class.getMethod("runDocumentAnalysis",
                        SeoDocumentAnalysisRunReqVO.class),
                PostMapping.class, "/document/run", "@ss.hasPermission('seo:analysis:run')");
        assertEndpoint(SeoAnalysisController.class.getMethod("getAnalysis", Long.class),
                GetMapping.class, "/{id}", "@ss.hasPermission('seo:analysis:query')");
        assertEndpoint(SeoAnalysisController.class.getMethod("getKeywords", Long.class),
                GetMapping.class, "/{id}/keywords", "@ss.hasPermission('seo:analysis:query')");
        assertEndpoint(SeoAnalysisController.class.getMethod("getKeyword", Long.class, Long.class),
                GetMapping.class, "/{id}/keywords/{keywordAnalysisId}",
                "@ss.hasPermission('seo:analysis:query')");
        assertEndpoint(SeoAnalysisController.class.getMethod("rerunAnalysis", Long.class,
                        SeoAnalysisRerunReqVO.class),
                PostMapping.class, "/{id}/rerun", "@ss.hasPermission('seo:analysis:run')");
        assertEndpoint(SeoAnalysisController.class.getMethod("compareAnalysis", Long.class, Long.class),
                GetMapping.class, "/{id}/compare", "@ss.hasPermission('seo:analysis:query')");
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
