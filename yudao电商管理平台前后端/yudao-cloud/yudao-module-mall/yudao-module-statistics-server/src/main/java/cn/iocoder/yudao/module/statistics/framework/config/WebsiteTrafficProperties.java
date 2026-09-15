package cn.iocoder.yudao.module.statistics.framework.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
@Component @Data @ConfigurationProperties(prefix="yudao.statistics.website")
public class WebsiteTrafficProperties {
    private Map<String, LocalDate> enabledFrom = new HashMap<>();
}
