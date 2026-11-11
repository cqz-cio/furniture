package cn.iocoder.yudao.module.seo.local;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.seo.service.page.WebsitePageService;
import cn.iocoder.yudao.module.seo.service.blog.WebsiteBlogService;
import cn.iocoder.yudao.module.seo.controller.admin.page.WebsitePageController;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.*;
import cn.iocoder.yudao.module.seo.controller.admin.blog.WebsiteBlogController;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.*;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

@org.springframework.test.context.TestPropertySource(properties={"spring.config.location=classpath:/application-unit-test.yaml","spring.cloud.nacos.config.enabled=false","spring.cloud.nacos.discovery.enabled=false"})
@Import(LocalCmsConfiguration.class)
class TablewareCmsServiceTest extends BaseDbUnitTest {
 @Resource cn.iocoder.yudao.module.seo.service.operations.WebsiteOperationsService operations;
 void approve(String kind,String target,int version){approve(operations,kind,target,version);}
 static void approve(cn.iocoder.yudao.module.seo.service.operations.WebsiteOperationsService ops,String kind,String target,int version){
  var r=ops.submitReview(JsonUtils.parseTree("{\"siteId\":1,\"kind\":\""+kind+"\",\"targetId\":\""+target+"\",\"version\":"+version+"}"));
  ops.decideReview(JsonUtils.parseTree("{\"siteId\":1,\"id\":"+r.path("id").asLong()+",\"version\":"+r.path("version").asInt()+",\"status\":\"approved\",\"note\":\"Test approval\"}"));
 }
 @Resource WebsitePageService pages;
 @Resource WebsiteBlogService blogs;
 @Resource WebsitePageController pageController;
 @Resource WebsiteBlogController blogController;
 @Resource SeoSiteConfigMapper sites;
 @Resource cn.iocoder.yudao.module.seo.dal.mysql.blog.WebsiteBlogArticleMapper articleMapper;
 @BeforeEach void setup() throws Exception {
  Files.writeString(Path.of("target/local-cms-classpath.txt"),System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")));
  for(long tenant:List.of(9001L,9002L)) {
   identity(tenant,"publisher");
   sites.insert(new SeoSiteConfigDO().setSiteId(1L).setSiteName("Trendz Green").setSiteUrl("http://127.0.0.1:4174").setPreviewBaseUrl("http://127.0.0.1:4174").setNavigationTemplate("TABLEWARE_B2B"));
  }
  identity(9001L,"publisher");
 }
 static void identity(long tenant,String role) {
  TenantContextHolder.setTenantId(tenant);
  SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(10L).setTenantId(tenant).setUserType(1),new MockHttpServletRequest());
  LocalCmsConfiguration.GRANTS.set(LocalCmsConfiguration.grants(role));
 }
 @AfterEach void cleanup(){TenantContextHolder.clear();SecurityContextHolder.clearContext();LocalCmsConfiguration.GRANTS.remove();}
 static WebsitePageKeyReqVO key(){return new WebsitePageKeyReqVO().setSiteId(1L).setLocale("en").setPageKey("catalog");}
 static WebsitePageVersionReqVO pv(int v){var r=new WebsitePageVersionReqVO();r.setSiteId(1L);r.setLocale("en");r.setPageKey("catalog");r.setExpectedVersion(v);return r;}
 static WebsitePageSaveReqVO save(int v,String json){var r=new WebsitePageSaveReqVO();r.setSiteId(1L);r.setLocale("en");r.setPageKey("catalog");r.setExpectedVersion(v);r.setContent(JsonUtils.parseTree(json));return r;}
 static String catalog(String name){return "{\"schemaVersion\":1,\"products\":[{\"slug\":\"cup\",\"name\":\""+name+"\",\"category\":\"cups-straws\",\"subcategory\":\"cups\",\"material\":\"Paper\",\"type\":\"cup\",\"image\":\"/assets/cup.webp\",\"description\":\"Cup\",\"sourceUrl\":\"https://www.trendzgreen.com/cup.html\",\"sourceDate\":\"2026-11-10\",\"specs\":[],\"notes\":[]}]}";}
 @Test void catalogDraftsVersionsHistoryAndPermissions() {
  identity(9001L,"editor");pageController.initialize(key());
  assertThatThrownBy(()->pageController.publish(pv(1))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  pages.saveDraft(save(1,catalog("Original")));
  assertThatThrownBy(()->pages.getPublished(key())).isInstanceOf(ServiceException.class);
  identity(9001L,"publisher");approve("catalog","catalog",2);pageController.publish(pv(2));
  pages.saveDraft(save(2,catalog("Changed")));
  assertThat(pages.getPublished(key()).getContent().toString()).contains("Original").doesNotContain("Changed");
  assertThatThrownBy(()->pages.saveDraft(save(2,catalog("Stale")))).isInstanceOf(ServiceException.class);
  var r=new WebsitePageRestoreReqVO();r.setSiteId(1L);r.setPageKey("catalog");r.setLocale("en");r.setExpectedVersion(3);r.setRevisionId(pages.getHistory(key()).get(0).getRevisionId());
  pages.restoreDraft(r);
  assertThat(pages.getDraft(key()).getVersion()).isEqualTo(4);
  assertThat(pages.getPublished(key()).getPublishedVersion()).isEqualTo(2);
 }
 @Test void catalogTenantAndTemplateBoundaries() {
  pages.initialize(key());approve("catalog","catalog",1);pages.publish(pv(1));long revision=pages.getHistory(key()).get(0).getRevisionId();
  identity(9002L,"publisher");
  assertThatThrownBy(()->pages.getPublished(key())).isInstanceOf(ServiceException.class);
  pages.initialize(key());
  var r=new WebsitePageRestoreReqVO();r.setSiteId(1L);r.setPageKey("catalog");r.setLocale("en");r.setExpectedVersion(1);r.setRevisionId(revision);
  assertThatThrownBy(()->pages.restoreDraft(r)).isInstanceOf(ServiceException.class);
  assertThatThrownBy(()->pages.initialize(new WebsitePageKeyReqVO().setSiteId(1L).setLocale("en").setPageKey("home"))).isInstanceOf(ServiceException.class);
 }
 @Test void invalidCatalogCannotBeSaved(){pages.initialize(key());for(String bad:List.of(catalog("Cup").replace("/assets/cup.webp","/assets/../secret"),catalog("Cup").replace("\"schemaVersion\":1","\"schemaVersion\":2"),catalog("Cup").replace("\"notes\":[]","\"html\":\"evil\",\"notes\":[]"))){assertThatThrownBy(()->pages.saveDraft(save(1,bad))).isInstanceOf(ServiceException.class);}}
 static WebsiteBlogArticleSaveReqVO article(String text) {
  var r=new WebsiteBlogArticleSaveReqVO();r.setSiteId(1L);r.setLocale("en");r.setSlug("legacy-article");r.setLegacyPath("/legacy-article.html");r.setTitle("Legacy article");r.setCategory("Sustainability");r.setLabel("Article");r.setSummary("Original article summary");r.setCoverImageUrl("/assets/cup.webp");r.setCoverImageAlt("Cup");
  var s=new WebsiteBlogSectionSaveReqVO();s.setId("article");s.setTitle("Article");s.setParagraphs(List.of());s.setBlocks(List.of(JsonUtils.parseTree("{\"type\":\"paragraph\",\"text\":\""+text+"\"}")));r.setSections(List.of(s));return r;
 }
 static WebsiteBlogVersionReqVO bv(long id,int v){var r=new WebsiteBlogVersionReqVO();r.setId(id);r.setVersion(v);return r;}
 @Test void articleDeletionRequiresPublisherAndExplicitOffline() {
  identity(9001L,"editor");long id=blogController.create(article("Original")).getData();
  assertThatThrownBy(()->blogController.delete(id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  identity(9001L,"publisher");approve("article",Long.toString(id),1);blogController.publish(bv(id,1));
  assertThatThrownBy(()->blogController.delete(id)).isInstanceOf(ServiceException.class)
    .extracting("code").isEqualTo(cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_ARTICLE_DELETE_REQUIRES_OFFLINE.getCode());
  assertThat(articleMapper.deleteByIdForTenant(id)).isZero();
  assertThat(blogs.getPublishedArticle(1L,"en","legacy-article")).isNotNull();
  identity(9001L,"editor");
  assertThatThrownBy(()->blogController.delete(id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  assertThatThrownBy(()->blogController.offline(bv(id,blogs.getArticle(id).getVersion()))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  identity(9001L,"publisher");blogController.offline(bv(id,blogs.getArticle(id).getVersion()));
  assertThat(blogs.getPublishedArticle(1L,"en","legacy-article")).isNull();
  assertThat(blogController.delete(id).getData()).isTrue();
  assertThatThrownBy(()->blogs.getArticle(id)).isInstanceOf(ServiceException.class);
 }
 @Test void publisherCanDeleteDraftOnlyWithinOwnTenant() {
  long id=blogController.create(article("Draft")).getData();
  identity(9002L,"publisher");
  assertThatThrownBy(()->blogController.delete(id)).isInstanceOf(ServiceException.class);
  assertThat(articleMapper.deleteByIdForTenant(id)).isZero();
  identity(9001L,"publisher");assertThat(blogController.delete(id).getData()).isTrue();
  assertThatThrownBy(()->blogs.getArticle(id)).isInstanceOf(ServiceException.class);
 }
 @Test void blogBlocksDraftSnapshotRestorePermissionAndTenantIsolation(){
  identity(9001L,"editor");long id=blogController.create(article("Original")).getData();
  assertThat(blogs.getPublishedArticle(1L,"en","legacy-article")).isNull();
  assertThatThrownBy(()->blogController.publish(bv(id,1))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  identity(9001L,"publisher");approve("article",Long.toString(id),1);blogController.publish(bv(id,1));
  var edit=article("Changed");edit.setVisible(false);edit.setPublishedAt(java.time.LocalDateTime.now().plusDays(3));edit.setSortOrder(999);edit.setId(id);edit.setVersion(blogs.getArticle(id).getVersion());blogs.updateArticle(edit);
  assertThat(blogs.getPublishedArticle(1L,"en","legacy-article").getSections().get(0).getBlocks().get(0).path("text").asText()).isEqualTo("Original");
  assertThat(blogs.getPublishedArticle(1L,"en","legacy-article").getPath()).isEqualTo("/legacy-article.html");
  assertThatThrownBy(()->blogs.updateArticle(edit)).isInstanceOf(ServiceException.class);
  var r=new WebsiteBlogRestoreReqVO();r.setId(id);r.setVersion(blogs.getArticle(id).getVersion());r.setRecordId(blogs.getPublishHistory(id).get(0).getId());blogs.restoreDraft(r);
  assertThat(blogs.getArticle(id).getSections().get(0).getBlocks().get(0).path("text").asText()).isEqualTo("Original");
  assertThat(blogs.getPublishHistory(id)).hasSize(1);
  identity(9002L,"publisher");assertThatThrownBy(()->blogs.getArticle(id)).isInstanceOf(ServiceException.class);
  assertThat(blogs.getPublishedPage(1L,"en",1,20).getItems()).isEmpty();
 }
 @Test void reservedArticleRoutesAreRejected(){for(String path:List.of("/products/","/cms/","/request-quote/","/request-samples/","/solutions/","/contact-us/","/manufacturing/","/quality-compliance/","/bags/","/product/cup/","/%63ms/","/other-article/")){var r=article("x");r.setLegacyPath(path);assertThatThrownBy(()->blogs.createArticle(r)).isInstanceOf(ServiceException.class);}}
 @Test void unsafeAndUnknownBlocksAreRejected(){for(String json:List.of("{\"type\":\"html\",\"text\":\"<script/>\"}","{\"type\":\"image\",\"src\":\"javascript:alert(1)\",\"alt\":\"x\"}","{\"type\":\"heading\",\"level\":1,\"text\":\"x\"}")){var r=article("x");r.getSections().get(0).setBlocks(List.of(JsonUtils.parseTree(json)));assertThatThrownBy(()->blogs.createArticle(r)).isInstanceOf(ServiceException.class);}}
}
