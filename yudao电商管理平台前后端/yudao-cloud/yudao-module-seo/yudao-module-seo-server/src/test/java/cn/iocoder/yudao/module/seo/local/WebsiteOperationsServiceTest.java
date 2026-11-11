package cn.iocoder.yudao.module.seo.local;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.operations.WebsiteOperationsController;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.operations.WebsiteDocumentMapper;
import cn.iocoder.yudao.module.seo.service.operations.WebsiteOperationsService;
import cn.iocoder.yudao.module.seo.service.page.WebsitePageService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.seo.local.TablewareCmsServiceTest.*;

@org.springframework.test.context.TestPropertySource(properties={"spring.config.location=classpath:/application-unit-test.yaml","spring.cloud.nacos.config.enabled=false","spring.cloud.nacos.discovery.enabled=false"})
@Import(LocalCmsConfiguration.class)
class WebsiteOperationsServiceTest extends BaseDbUnitTest {
 @Resource WebsiteOperationsService ops;
 @Resource WebsiteOperationsController controller;
 @Resource WebsitePageService pages;
 @Resource cn.iocoder.yudao.module.seo.service.blog.WebsiteBlogService blogs;
 @Resource SeoSiteConfigMapper sites;
 @Resource WebsiteDocumentMapper documents;
 @BeforeEach void setup(){for(long t:List.of(9001L,9002L)){identity(t,"publisher");sites.insert(new SeoSiteConfigDO().setSiteId(1L).setSiteName("Tableware").setSiteUrl("http://127.0.0.1:4174").setNavigationTemplate("TABLEWARE_B2B"));}identity(9001L,"publisher");sites.insert(new SeoSiteConfigDO().setSiteId(2L).setSiteName("Second site").setSiteUrl("http://127.0.0.1:4175").setNavigationTemplate("TABLEWARE_B2B"));}
 @AfterEach void cleanup(){TenantContextHolder.clear();SecurityContextHolder.clearContext();LocalCmsConfiguration.GRANTS.remove();}
 static ObjectNode json(String text){return (ObjectNode)JsonUtils.parseTree(text);}
 static ObjectNode action(long id,int version){return json("{\"siteId\":1}").put("id",id).put("version",version);}
 long file(){return ((Number)ops.upload(1L,new MockMultipartFile("file","local-test.pdf","application/pdf","%PDF-1.4\nLOCAL ACCEPTANCE ONLY\n%%EOF".getBytes(StandardCharsets.US_ASCII))).get("id")).longValue();}
 ObjectNode fields(long file){return json("{\"siteId\":1,\"title\":\"Local guide\",\"kind\":\"guide\",\"language\":\"en\",\"holder\":\"Test\",\"scope\":\"Local only\",\"market\":\"Test\",\"owner\":\"Reviewer\",\"issuedAt\":\"\",\"expiresAt\":\"\"}").put("assetId",file);}
 void approveDoc(ObjectNode doc){approve(ops,"document",doc.path("id").asText(),doc.path("version").asInt());}
 @Test void uploadIsPrivateDeduplicatedAndTenantScoped(){
  long id=file();assertThat(file()).isEqualTo(id);assertThat(ops.publicDocuments(1L)).isEmpty();
  assertThat(new String(ops.reviewFile(1L,id),StandardCharsets.US_ASCII)).contains("LOCAL ACCEPTANCE ONLY");
  assertThatThrownBy(()->ops.reviewFile(2L,id)).isInstanceOf(ServiceException.class);
  assertThatThrownBy(()->ops.upload(1L,new MockMultipartFile("file","bad.pdf","application/pdf","<script/>".getBytes()))).isInstanceOf(ServiceException.class);
  assertThatThrownBy(()->ops.saveDocument(fields(id).put("siteId",2))).isInstanceOf(ServiceException.class);
  identity(9002L,"publisher");assertThat(ops.state(1L).get("files")).asList().isEmpty();assertThatThrownBy(()->ops.saveDocument(fields(id))).isInstanceOf(ServiceException.class);assertThatThrownBy(()->ops.reviewFile(1L,id)).isInstanceOf(ServiceException.class);assertThat(file()).isNotEqualTo(id);
 }
 @Test void documentPublishesOnlyApprovedVersionAndDraftDoesNotLeak(){
  var doc=ops.saveDocument(fields(file()));long id=doc.path("id").asLong();
  assertThatThrownBy(()->ops.publishDocument(action(id,1))).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(OPERATION_APPROVAL_REQUIRED.getCode());
  approveDoc(doc);ops.publishDocument(action(id,1));assertThat(ops.publicDocuments(1L).get(0).path("title").asText()).isEqualTo("Local guide");assertThat(new String(ops.download(1L,id),StandardCharsets.US_ASCII)).contains("LOCAL ACCEPTANCE ONLY");
  var changed=doc.deepCopy().put("siteId",1).put("title","Private changed title");ops.saveDocument(changed);
  assertThat(ops.publicDocuments(1L).get(0).path("title").asText()).isEqualTo("Local guide");assertThatThrownBy(()->ops.publishDocument(action(id,2))).isInstanceOf(ServiceException.class);
  assertThatThrownBy(()->ops.saveDocument(changed)).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(OPERATION_VERSION_CONFLICT.getCode());
 }
 @Test void expiredPublishedSnapshotCannotDownloadAndOfflineRequiredBeforeDelete(){
  var doc=ops.saveDocument(fields(file()));long id=doc.path("id").asLong();approveDoc(doc);ops.publishDocument(action(id,1));
  assertThatThrownBy(()->ops.deleteDocument(action(id,1))).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(OPERATION_OFFLINE_REQUIRED.getCode());
  var row=documents.get(1L,id,false);row.setPublishedJson(json(row.getPublishedJson()).put("expiresAt","2000-01-01").toString());documents.updateById(row);
  assertThat(ops.publicDocuments(1L)).isEmpty();assertThatThrownBy(()->ops.download(1L,id)).isInstanceOf(ServiceException.class);
  var off=ops.offlineDocument(action(id,1));assertThat(ops.deleteDocument(action(id,off.path("version").asInt()))).isTrue();
 }
 @Test void datesCertificateRequirementsAndCrossTenantDownloadsAreChecked(){
  long file=file();assertThatThrownBy(()->ops.saveDocument(fields(file).put("kind","certificate"))).isInstanceOf(ServiceException.class);
  assertThatThrownBy(()->ops.saveDocument(fields(file).put("expiresAt","2026-02-30"))).isInstanceOf(ServiceException.class);
  assertThatThrownBy(()->ops.saveDocument(fields(file).put("issuedAt","2099-01-01"))).isInstanceOf(ServiceException.class);
  var expired=ops.saveDocument(fields(file).put("expiresAt","2000-01-01"));approveDoc(expired);assertThatThrownBy(()->ops.publishDocument(action(expired.path("id").asLong(),1))).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(OPERATION_DOCUMENT_EXPIRED.getCode());
  var doc=ops.saveDocument(fields(file));approveDoc(doc);ops.publishDocument(action(doc.path("id").asLong(),1));identity(9002L,"publisher");assertThatThrownBy(()->ops.download(1L,doc.path("id").asLong())).isInstanceOf(ServiceException.class);
 }
 @Test void directCatalogPublicationRequiresApprovalAndEditsInvalidateIt(){
  pages.initialize(key());pages.saveDraft(save(1,catalog("Original")));
  assertThatThrownBy(()->pages.publish(pv(2))).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(OPERATION_APPROVAL_REQUIRED.getCode());
  approve(ops,"catalog","catalog",2);pages.saveDraft(save(2,catalog("Changed")));
  assertThatThrownBy(()->pages.publish(pv(3))).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(OPERATION_APPROVAL_REQUIRED.getCode());
  approve(ops,"catalog","catalog",3);pages.publish(pv(3));assertThat(pages.getPublished(key()).getContent().toString()).contains("Changed");
 }
 @Test void reviewersCannotApproveStaleContentAndEditorsCannotApprove(){
  pages.initialize(key());var request=json("{\"siteId\":1,\"kind\":\"catalog\",\"targetId\":\"catalog\",\"version\":1}");
  identity(9001L,"editor");var review=ops.submitReview(request);var decision=action(review.path("id").asLong(),1).put("status","approved").put("note","");
  assertThatThrownBy(()->controller.decide(decision)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  pages.saveDraft(save(1,catalog("Changed")));identity(9001L,"publisher");assertThatThrownBy(()->ops.decideReview(decision)).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(OPERATION_VERSION_CONFLICT.getCode());
 }
 @Test void taskUpdatesUseVersionsAndInspectionPersistsRealMissingSpecification(){
  pages.initialize(key());pages.saveDraft(save(1,catalog("Cup")));
  var task=ops.saveTask(json("{\"siteId\":1,\"title\":\"Review\",\"owner\":\"Reviewer\",\"due\":\"2000-01-01\",\"status\":\"open\"}"));
  var update=task.deepCopy().put("siteId",1).put("status","done");ops.saveTask(update);assertThatThrownBy(()->ops.saveTask(update)).isInstanceOf(ServiceException.class);
  assertThat(ops.check(1L).path("issues").toString()).contains("规格待供应商确认");assertThat(ops.state(1L).get("checks")).asList().hasSize(1);
  identity(9002L,"publisher");assertThatThrownBy(()->ops.deleteTask(action(task.path("id").asLong(),2))).isInstanceOf(ServiceException.class);
 }
 @Test void backupChecksumTenantAndDraftOnlyRestoreAreEnforced(){
  pages.initialize(key());pages.saveDraft(save(1,catalog("Original")));approve(ops,"catalog","catalog",2);pages.publish(pv(2));
  ObjectNode backup=(ObjectNode)JsonUtils.parseTree(JsonUtils.toJsonString(ops.backup(1L)));
  ObjectNode request=json("{\"siteId\":1,\"kind\":\"catalog\",\"version\":3}");request.set("backup",backup);
  pages.saveDraft(save(2,catalog("Private change")));ops.restore(request);assertThat(pages.getDraft(key()).getContent().toString()).contains("Original");assertThat(pages.getPublished(key()).getVersion()).isEqualTo(2);assertThatThrownBy(()->pages.publish(pv(4))).isInstanceOf(ServiceException.class);
  ObjectNode tampered=backup.deepCopy();((ObjectNode)tampered.path("payload").path("catalog").path("products").get(0)).put("name","Tampered");request.set("backup",tampered);assertThatThrownBy(()->ops.checkBackup(request)).isInstanceOf(ServiceException.class);
  request.set("backup",backup);identity(9002L,"publisher");assertThatThrownBy(()->ops.checkBackup(request)).isInstanceOf(ServiceException.class);
 }
 @Test void articleBackupRestoresWritableFieldsAndFullBlocksOnly(){
  pages.initialize(key());long id=blogs.createArticle(article("Original full block"));approve(ops,"article",Long.toString(id),1);blogs.publishArticle(bv(id,1));
  ObjectNode backup=(ObjectNode)JsonUtils.parseTree(JsonUtils.toJsonString(ops.backup(1L)));
  var changed=article("Private new block");changed.setId(id);changed.setVersion(blogs.getArticle(id).getVersion());blogs.updateArticle(changed);
  var request=action(id,blogs.getArticle(id).getVersion()).put("kind","article");request.set("backup",backup);ops.restore(request);
  assertThat(blogs.getArticle(id).getSections().get(0).getBlocks().get(0).path("text").asText()).isEqualTo("Original full block");
  assertThat(blogs.getPublishedArticle(1L,"en","legacy-article").getSections().get(0).getBlocks().get(0).path("text").asText()).isEqualTo("Original full block");
  assertThatThrownBy(()->blogs.publishArticle(bv(id,blogs.getArticle(id).getVersion()))).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(OPERATION_APPROVAL_REQUIRED.getCode());
 }
 @Test void documentBackupRestoresDraftAndVerifiesStoredPdfBytes(){
  pages.initialize(key());var doc=ops.saveDocument(fields(file()));long id=doc.path("id").asLong();approveDoc(doc);ops.publishDocument(action(id,1));
  ObjectNode backup=(ObjectNode)JsonUtils.parseTree(JsonUtils.toJsonString(ops.backup(1L)));ops.saveDocument(doc.deepCopy().put("siteId",1).put("title","Private change"));
  var request=action(id,2).put("kind","document");request.set("backup",backup);ops.restore(request);
  assertThat(documents.get(1L,id,false).getDraftJson()).contains("Local guide").doesNotContain("Private change");assertThat(documents.get(1L,id,false).getVersion()).isEqualTo(3);
  assertThat(ops.publicDocuments(1L).get(0).path("title").asText()).isEqualTo("Local guide");assertThatThrownBy(()->ops.publishDocument(action(id,3))).isInstanceOf(ServiceException.class);
 }
 @Test void privateFilesDeleteOnlyAfterAllDraftAndPublishedReferencesAreRemoved(){
  long id=file();var doc=ops.saveDocument(fields(id));long docId=doc.path("id").asLong();
  assertThatThrownBy(()->ops.deleteFile(action(id,1))).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(OPERATION_FILE_REFERENCED.getCode());
  approveDoc(doc);ops.publishDocument(action(docId,1));
  long replacement=((Number)ops.upload(1L,new MockMultipartFile("file","replacement.pdf","application/pdf","%PDF-1.4\nOTHER TEST FILE\n%%EOF".getBytes(StandardCharsets.US_ASCII))).get("id")).longValue();
  ops.saveDocument(doc.deepCopy().put("siteId",1).put("assetId",replacement));
  assertThatThrownBy(()->ops.deleteFile(action(id,1))).isInstanceOf(ServiceException.class);
  ops.offlineDocument(action(docId,2));identity(9002L,"publisher");assertThatThrownBy(()->ops.deleteFile(action(id,1))).isInstanceOf(ServiceException.class);
  identity(9001L,"editor");assertThatThrownBy(()->controller.deleteFile(action(id,1))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  identity(9001L,"publisher");assertThat(ops.deleteFile(action(id,1))).isTrue();assertThatThrownBy(()->ops.reviewFile(1L,id)).isInstanceOf(ServiceException.class);
  assertThat(file()).isNotEqualTo(id);
 }
}
