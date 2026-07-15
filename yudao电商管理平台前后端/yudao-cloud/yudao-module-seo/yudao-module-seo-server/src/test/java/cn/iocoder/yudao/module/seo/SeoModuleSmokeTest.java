package cn.iocoder.yudao.module.seo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SeoModuleSmokeTest {

    @Test
    void seoApplicationShouldBeASpringBootApplication() {
        assertThat(AnnotatedElementUtils.hasAnnotation(
                SeoServerApplication.class, SpringBootApplication.class)).isTrue();
    }

}
