package cn.iocoder.yudao.module.seo.local;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.service.page.WebsitePageService;
import cn.iocoder.yudao.module.seo.service.blog.WebsiteBlogService;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.*;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;

/** Explicit development fixture. Uses real CMS services/SQL/controllers; never starts ERP integrations. */
public final class LocalCmsMain {
 static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 @RestControllerAdvice public static class Errors {
  @ExceptionHandler(Exception.class) public ResponseEntity<Map<String,Object>> error(Exception e){
   int status=e instanceof org.springframework.security.access.AccessDeniedException?403:400;
   if(e instanceof ServiceException se && (se.getCode()==cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.PAGE_VERSION_CONFLICT.getCode() || se.getCode()==cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_ARTICLE_VERSION_CONFLICT.getCode()))status=409;
   return ResponseEntity.status(status).body(Map.of("code",e instanceof ServiceException s?s.getCode():status,"msg",e instanceof ServiceException?e.getMessage():"Request rejected","data",Map.of()));
  }
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("Workspace root required");
  Path root=Path.of(args[0]).toAbsolutePath().normalize(), work=root.resolve("work/phase2-cms-migration");Files.createDirectories(work);
  var ctx=new SpringApplicationBuilder(LocalCmsConfiguration.class).web(WebApplicationType.NONE).profiles("unit-test","local-cms").run(
   "--spring.config.location=classpath:/application-unit-test.yaml",
   "--spring.datasource.url=jdbc:h2:file:"+work.resolve("cms-h2").toString().replace('\\','/')+";MODE=MYSQL;DATABASE_TO_UPPER=false;NON_KEYWORDS=value",
   "--spring.main.banner-mode=off");
  TablewareCmsServiceTest.identity(9001L,"publisher");
  var sites=ctx.getBean(SeoSiteConfigMapper.class);
  if(sites.selectBySiteId(1L)==null)sites.insert(new SeoSiteConfigDO().setSiteId(1L).setSiteName("Trendz Green local snapshot").setSiteUrl("http://127.0.0.1:4174").setPreviewBaseUrl("http://127.0.0.1:4174").setNavigationTemplate("TABLEWARE_B2B"));
  var pages=ctx.getBean(WebsitePageService.class);var key=TablewareCmsServiceTest.key();
  boolean missing=false;try{pages.getDraft(key);}catch(ServiceException e){if(e.getCode()==cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.PAGE_NOT_INITIALIZED.getCode())missing=true;else throw e;}
  if(missing){pages.initialize(key);ObjectNode content=JSON.createObjectNode().put("schemaVersion",1);content.set("products",JSON.readTree(root.resolve("website/trendzgreen-homepage/src/catalog-data.json").toFile()).path("products"));pages.saveDraft(TablewareCmsServiceTest.save(1,content.toString()));TablewareCmsServiceTest.approve(ctx.getBean(cn.iocoder.yudao.module.seo.service.operations.WebsiteOperationsService.class),"catalog","catalog",2);pages.publish(TablewareCmsServiceTest.pv(2));}
  var blogs=ctx.getBean(WebsiteBlogService.class);
  for(JsonNode a:JSON.readTree(root.resolve("website/trendzgreen-homepage/src/legacy-articles.json").toFile()).path("articles")){
   var query=new WebsiteBlogArticlePageReqVO();query.setSiteId(1L);query.setLocale("en");query.setPageSize(100);
   if(blogs.getArticlePage(query).getList().stream().anyMatch(x->x.getSlug().equals(a.path("slug").asText())))continue;
   var r=new WebsiteBlogArticleSaveReqVO();r.setSiteId(1L);r.setLocale("en");r.setSlug(a.path("slug").asText());r.setLegacyPath(a.path("path").asText());r.setTitle(a.path("title").asText());r.setCategory("Journal");r.setLabel("Article");String summary=a.path("excerpt").asText();if(summary.isBlank()){summary=a.path("title").asText();for(JsonNode block:a.path("blocks"))if(block.path("type").asText().equals("paragraph")&&!block.path("text").asText().isBlank()){summary=block.path("text").asText();break;}}r.setSummary(summary.substring(0,Math.min(summary.length(),600)));String cover=a.path("image").asText();r.setCoverImageUrl(cover.isBlank()?"/assets/products/pla-cold-cup.webp":cover);r.setCoverImageAlt(cover.isBlank()?"Trendz Green product catalog":a.path("title").asText());r.setPublishedAt(LocalDateTime.ofInstant(Instant.parse(a.path("publishedAt").asText()),ZoneOffset.UTC));
   var section=new WebsiteBlogSectionSaveReqVO();section.setId("content");section.setTitle("Article");section.setParagraphs(List.of());List<JsonNode> blocks=new ArrayList<>();a.path("blocks").forEach(blocks::add);section.setBlocks(blocks);r.setSections(List.of(section));
   long id=blogs.createArticle(r);blogs.publishArticle(TablewareCmsServiceTest.bv(id,blogs.getArticle(id).getVersion()));
  }
  TenantContextHolder.clear();SecurityContextHolder.clearContext();LocalCmsConfiguration.GRANTS.remove();
  MockMvc admin=MockMvcBuilders.standaloneSetup(ctx.getBean(cn.iocoder.yudao.module.seo.controller.admin.page.WebsitePageController.class),ctx.getBean(cn.iocoder.yudao.module.seo.controller.admin.blog.WebsiteBlogController.class)).setMessageConverters(new MappingJackson2HttpMessageConverter(JSON)).setControllerAdvice(new Errors()).build();
  MockMvc app=MockMvcBuilders.standaloneSetup(ctx.getBean(cn.iocoder.yudao.module.seo.controller.app.page.AppWebsitePageController.class),ctx.getBean(cn.iocoder.yudao.module.seo.controller.app.blog.AppWebsiteBlogController.class)).setMessageConverters(new MappingJackson2HttpMessageConverter(JSON)).setControllerAdvice(new Errors()).build();
  Map<String,Map<String,String>> roles=new LinkedHashMap<>();SecureRandom random=new SecureRandom();for(String role:List.of("viewer","editor","publisher")){byte[] token=new byte[32],pass=new byte[18];random.nextBytes(token);random.nextBytes(pass);roles.put(role,Map.of("token",Base64.getUrlEncoder().withoutPadding().encodeToString(token),"password",Base64.getUrlEncoder().withoutPadding().encodeToString(pass)));}
  JSON.writerWithDefaultPrettyPrinter().writeValue(work.resolve("local-runtime-private.json").toFile(),Map.of("baseUrl","http://127.0.0.1:48081","tenantId",9001,"siteId",1,"roles",roles));
  HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),48081),32);
  server.createContext("/",exchange->{try{
   String path=exchange.getRequestURI().getPath();if(path.equals("/health")){respond(exchange,200,"{\"status\":\"ok\",\"mode\":\"isolated-local-cms\",\"database\":\"H2-file\"}");return;}
   boolean isAdmin=path.startsWith("/admin-api/");if(!isAdmin&&!path.startsWith("/app-api/")){respond(exchange,404,"{}");return;}
   String role=null,auth=exchange.getRequestHeaders().getFirst("Authorization");for(var entry:roles.entrySet())if(auth!=null&&MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),("Bearer "+entry.getValue().get("token")).getBytes(StandardCharsets.UTF_8)))role=entry.getKey();
   if(isAdmin&&role==null){respond(exchange,401,"{\"code\":401,\"msg\":\"Authentication required\"}");return;}
   byte[] body=exchange.getRequestBody().readNBytes(4*1024*1024+1);if(body.length>4*1024*1024){respond(exchange,413,"{}");return;}
   TablewareCmsServiceTest.identity(9001L,role==null?"anonymous":role);if(role==null)LocalCmsConfiguration.GRANTS.set(Set.of());
   String route=path.substring(isAdmin?10:8)+(exchange.getRequestURI().getRawQuery()==null?"":"?"+exchange.getRequestURI().getRawQuery());
   var request=MockMvcRequestBuilders.request(HttpMethod.valueOf(exchange.getRequestMethod()),URI.create(route)).content(body);
   exchange.getRequestHeaders().forEach((name,values)->{if(!Set.of("host","authorization","tenant-id").contains(name.toLowerCase(Locale.ROOT)))request.header(name,values.toArray());});
   var result=(isAdmin?admin:app).perform(request).andReturn().getResponse();
   exchange.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");exchange.getResponseHeaders().set("Cache-Control","no-store");
   byte[] bytes=result.getContentAsByteArray();exchange.sendResponseHeaders(result.getStatus(),bytes.length);exchange.getResponseBody().write(bytes);
  }catch(Exception e){System.err.println("Local CMS request rejected: "+e.getClass().getSimpleName());respond(exchange,500,"{\"code\":500,\"msg\":\"Local request failed\"}");}finally{TenantContextHolder.clear();SecurityContextHolder.clearContext();LocalCmsConfiguration.GRANTS.remove();exchange.close();}});
  server.setExecutor(Executors.newFixedThreadPool(4));server.start();Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(1);ctx.close();}));System.out.println("LOCAL_CMS_READY 127.0.0.1:48081 — real CMS services; isolated H2; local published seed snapshot");
 }
 static void respond(HttpExchange x,int status,String text)throws java.io.IOException{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);}
}
