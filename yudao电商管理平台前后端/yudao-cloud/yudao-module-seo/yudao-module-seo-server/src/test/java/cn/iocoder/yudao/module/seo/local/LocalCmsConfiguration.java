package cn.iocoder.yudao.module.seo.local;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.seo.service.page.WebsitePageService;
import cn.iocoder.yudao.module.seo.service.blog.WebsiteBlogServiceImpl;
import cn.iocoder.yudao.module.seo.service.config.SeoSiteConfigServiceImpl;
import cn.iocoder.yudao.module.seo.dal.redis.page.*;
import cn.iocoder.yudao.module.seo.dal.redis.blog.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.Mockito.mock;

/** Test-classpath only: real services, SQL and permission proxies, no production integrations. */
@Configuration(proxyBeanMethods=false)
@EnableMethodSecurity(jsr250Enabled=true)
@Import({BaseDbUnitTest.Application.class, ValidationAutoConfiguration.class, WebsitePageService.class,
 WebsiteBlogServiceImpl.class, SeoSiteConfigServiceImpl.class,
 cn.iocoder.yudao.module.seo.service.operations.WebsiteApprovalGuard.class,
 cn.iocoder.yudao.module.seo.service.operations.WebsiteOperationsService.class,
 cn.iocoder.yudao.module.seo.controller.admin.operations.WebsiteOperationsController.class,
 cn.iocoder.yudao.module.seo.controller.admin.page.WebsitePageController.class,
 cn.iocoder.yudao.module.seo.controller.app.page.AppWebsitePageController.class,
 cn.iocoder.yudao.module.seo.controller.admin.blog.WebsiteBlogController.class,
 cn.iocoder.yudao.module.seo.controller.app.blog.AppWebsiteBlogController.class})
public class LocalCmsConfiguration {
    public static final ThreadLocal<Set<String>> GRANTS=ThreadLocal.withInitial(Set::of);
    public static Set<String> grants(String role) {
        Set<String> permissions=new HashSet<>(Set.of("seo:page:query","seo:blog:query","seo:operations:query"));
        if(Set.of("editor","publisher").contains(role))permissions.addAll(Set.of("seo:page:update","seo:page:preview","seo:blog:create","seo:blog:update","seo:blog:preview","seo:operations:update"));
        if("publisher".equals(role))permissions.addAll(Set.of("seo:page:publish","seo:blog:publish","seo:blog:delete","seo:operations:approve","seo:operations:publish"));
        return Set.copyOf(permissions);
    }
    public static class Permissions { public boolean hasPermission(String permission){return GRANTS.get().contains(permission);} }
    @Bean(name="ss") Permissions permissions(){return new Permissions();}
    @Bean org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate(){return mock(org.springframework.data.redis.core.StringRedisTemplate.class);}
    @Bean cn.iocoder.yudao.module.seo.service.media.WebsiteMediaService mediaService(){return mock(cn.iocoder.yudao.module.seo.service.media.WebsiteMediaService.class);}
    @Bean cn.iocoder.yudao.module.infra.api.file.FileApi fileApi(){return mock(cn.iocoder.yudao.module.infra.api.file.FileApi.class);}
    @Bean cn.iocoder.yudao.framework.tenant.config.TenantProperties tenantProperties(){return new cn.iocoder.yudao.framework.tenant.config.TenantProperties();}
    @Bean @Lazy(false) com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor tenantLine(
        cn.iocoder.yudao.framework.tenant.config.TenantProperties properties,
        com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor) {
        var inner=new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(properties));
        cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils.addInterceptor(interceptor,inner,0);return inner;
    }
    private record Timed<T>(T value,long expires){}
    private static <T>T take(Map<String,Timed<T>> map,String key,boolean consume){Timed<T> v=consume?map.remove(key):map.get(key);return v==null||v.expires<System.currentTimeMillis()?null:v.value;}
    @Bean WebsitePagePreviewRedisDAO pagePreviewDAO(){return new WebsitePagePreviewRedisDAO(){
        final Map<String,Timed<WebsitePagePreviewGrant>> tickets=new ConcurrentHashMap<>(),sessions=new ConcurrentHashMap<>();
        public void setTicket(String t,WebsitePagePreviewGrant g){tickets.put(t,new Timed<>(g,System.currentTimeMillis()+120000));}
        public WebsitePagePreviewGrant consumeTicket(String t){return take(tickets,t,true);}
        public void setSession(String t,WebsitePagePreviewGrant g){sessions.put(t,new Timed<>(g,System.currentTimeMillis()+900000));}
        public WebsitePagePreviewGrant getSession(String t){return take(sessions,t,false);}
    };}
    @Bean WebsiteBlogPreviewRedisDAO blogPreviewDAO(){return new WebsiteBlogPreviewRedisDAO(){
        final Map<String,Timed<WebsiteBlogPreviewGrant>> tickets=new ConcurrentHashMap<>(),sessions=new ConcurrentHashMap<>();
        public void setTicket(String t,WebsiteBlogPreviewGrant g,Duration ttl){tickets.put(t,new Timed<>(g,System.currentTimeMillis()+ttl.toMillis()));}
        public WebsiteBlogPreviewGrant consumeTicket(String t){return take(tickets,t,true);}
        public void setSession(String t,WebsiteBlogPreviewGrant g,Duration ttl){sessions.put(t,new Timed<>(g,System.currentTimeMillis()+ttl.toMillis()));}
        public WebsiteBlogPreviewGrant getSession(String t){return take(sessions,t,false);}
    };}
}
