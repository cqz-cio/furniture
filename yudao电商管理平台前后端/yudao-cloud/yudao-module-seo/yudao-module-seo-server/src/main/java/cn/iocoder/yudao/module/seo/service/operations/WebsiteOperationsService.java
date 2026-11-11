package cn.iocoder.yudao.module.seo.service.operations;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.*;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.operations.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.blog.WebsiteBlogArticleDO;
import cn.iocoder.yudao.module.seo.dal.mysql.operations.*;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.blog.WebsiteBlogArticleMapper;
import cn.iocoder.yudao.module.seo.service.page.WebsitePageService;
import cn.iocoder.yudao.module.seo.service.blog.WebsiteBlogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;

/** Tenant/site-scoped ERP operations. Mutations serialize on the existing site row. */
@Service
public class WebsiteOperationsService {
    @Resource private WebsiteOperationRecordMapper records;
    @Resource private WebsiteDocumentMapper documents;
    @Resource private WebsitePrivateFileMapper files;
    @Resource private SeoSiteConfigMapper sites;
    @Resource private WebsiteBlogArticleMapper articles;
    @Resource private WebsitePageService pages;
    @Resource private WebsiteBlogService blogs;
    @Resource private WebsiteApprovalGuard approval;

    private Long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private String actor() { return Objects.toString(SecurityFrameworkUtils.getLoginUserId(), "system"); }
    private static String now() { return Instant.now().toString(); }
    private void site(Long id, boolean lock) {
        if(id==null || id<=0)throw exception(OPERATION_INVALID);
        var row=lock?sites.selectBySiteIdForUpdate(id):sites.selectBySiteId(id);
        if(row==null || !tenant().equals(row.getTenantId()) || !"TABLEWARE_B2B".equals(row.getNavigationTemplate()))throw exception(PAGE_SITE_UNAVAILABLE);
    }
    private static ObjectNode object() { return (ObjectNode)JsonUtils.parseTree("{}"); }
    private static ObjectNode parse(String value) { return (ObjectNode)JsonUtils.parseTree(value); }
    private static String text(JsonNode body,String key,int max,boolean required) {
        JsonNode value=body.path(key);
        if(!value.isMissingNode() && !value.isNull() && !value.isTextual())throw exception(OPERATION_INVALID);
        String result=value.asText("").strip();
        if(result.length()>max || (required&&result.isBlank()) || result.indexOf('\0')>=0)throw exception(OPERATION_INVALID);
        return result;
    }
    private static long number(JsonNode body,String key) {
        var value=body.path(key);if(!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue()<=0)throw exception(OPERATION_INVALID);return value.longValue();
    }
    private static int version(JsonNode body) { long v=number(body,"version");if(v>Integer.MAX_VALUE)throw exception(OPERATION_INVALID);return (int)v; }
    private static void version(int actual,int expected) { if(actual!=expected)throw exception(OPERATION_VERSION_CONFLICT); }
    private static LocalDate date(String value,boolean required) {
        if(value.isBlank()&&!required)return null;
        try { if(!value.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();return LocalDate.parse(value); }
        catch(RuntimeException error){throw exception(OPERATION_INVALID);}
    }
    private WebsiteDocumentDO document(Long siteId,Long id,boolean lock) {
        var row=documents.get(siteId,id,lock);if(row==null)throw exception(OPERATION_NOT_FOUND);return row;
    }
    private WebsitePrivateFileDO file(Long siteId,Long id) {
        var row=files.get(siteId,id);if(row==null)throw exception(OPERATION_NOT_FOUND);return row;
    }
    private byte[] bytes(Long siteId,Long id) {
        var row=files.withContent(tenant(),siteId,id);if(row==null || row.getContent()==null)throw exception(OPERATION_NOT_FOUND);return row.getContent();
    }
    private WebsiteOperationRecordDO record(Long siteId,Long id,String kind) {
        var row=records.locked(siteId,id);if(row==null || !kind.equals(row.getKind()))throw exception(OPERATION_NOT_FOUND);return row;
    }
    private WebsiteOperationRecordDO insert(Long siteId,String kind,ObjectNode body) {
        var row=new WebsiteOperationRecordDO().setSiteId(siteId).setKind(kind).setTargetKind("").setTargetKey("").setContentVersion(0).setStatus("").setVersion(1).setPayloadJson(body.toString());
        row.setTenantId(tenant());records.insert(row);return row;
    }
    private void audit(Long siteId,String action,String target) { insert(siteId,"audit",object().put("at",now()).put("actor",actor()).put("action",action).put("target",target)); }
    private ObjectNode view(WebsiteOperationRecordDO row) {
        ObjectNode result=parse(row.getPayloadJson()).put("id",row.getId()).put("version",row.getVersion());
        if("review".equals(row.getKind()))result.put("kind",row.getTargetKind()).put("targetId",row.getTargetKey()).put("contentVersion",row.getContentVersion()).put("status",row.getStatus());
        return result;
    }
    private ObjectNode view(WebsiteDocumentDO row) {
        ObjectNode result=parse(row.getDraftJson()).put("id",row.getId()).put("version",row.getVersion());
        result.set("published",row.getPublishedJson()==null?null:parse(row.getPublishedJson()));return result;
    }
    private Map<String,Object> view(WebsitePrivateFileDO row) { return Map.of("id",row.getId(),"name",row.getName(),"size",row.getSize(),"sha256",row.getSha256()); }

    public Map<String,Object> state(Long siteId) {
        site(siteId,false);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("documents",documents.documents(siteId).stream().map(this::view).toList());
        result.put("files",files.selectList(files.scope(siteId).orderByDesc(WebsitePrivateFileDO::getId)).stream().map(this::view).toList());
        for(String kind:List.of("review","task","check","audit"))result.put(kind.equals("audit")?"audit":kind+"s",records.records(siteId,kind).stream().map(this::view).toList());
        return result;
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> upload(Long siteId,MultipartFile input) {
        site(siteId,true);
        if(input==null || input.isEmpty() || input.getSize()>5*1024*1024)throw exception(OPERATION_INVALID);
        byte[] content;
        try { content=input.getBytes(); } catch(java.io.IOException ex){throw exception(OPERATION_INVALID);}
        if(content.length<12 || !new String(content,0,5,StandardCharsets.US_ASCII).equals("%PDF-") || !new String(content,Math.max(0,content.length-2048),Math.min(2048,content.length),StandardCharsets.ISO_8859_1).contains("%%EOF"))throw exception(OPERATION_INVALID);
        String digest=hash(content);
        var existing=files.selectOne(files.scope(siteId).eq(WebsitePrivateFileDO::getSha256,digest));if(existing!=null)return view(existing);
        if(files.selectCount(files.scope(siteId))>=300)throw exception(OPERATION_LIMIT);
        String name=Objects.toString(input.getOriginalFilename(),"document.pdf").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
        if(name.isBlank()||name.length()>160)throw exception(OPERATION_INVALID);
        var row=new WebsitePrivateFileDO().setSiteId(siteId).setName(name).setSize((long)content.length).setSha256(digest).setContent(content);row.setTenantId(tenant());files.insert(row);
        audit(siteId,"upload-private-pdf",row.getId().toString());return view(row);
    }
    private ObjectNode documentFields(Long siteId,JsonNode input) {
        ObjectNode result=object();
        for(String key:List.of("title","kind","language","holder","scope","market","owner","issuedAt","expiresAt"))result.put(key,text(input,key,key.equals("scope")?2000:240,!Set.of("issuedAt","expiresAt").contains(key)));
        if(!Set.of("certificate","guide","catalog","specification").contains(result.path("kind").asText()) || !result.path("language").asText().matches("[a-z]{2}(?:-[A-Za-z]{2,4})?"))throw exception(OPERATION_INVALID);
        LocalDate issue=date(result.path("issuedAt").asText(),false),expiry=date(result.path("expiresAt").asText(),"certificate".equals(result.path("kind").asText()));
        if(issue!=null&&issue.isAfter(LocalDate.now()) || issue!=null&&expiry!=null&&expiry.isBefore(issue))throw exception(OPERATION_INVALID);
        long assetId=number(input,"assetId");file(siteId,assetId);result.put("assetId",assetId);return result;
    }
    @Transactional(rollbackFor=Exception.class)
    public ObjectNode saveDocument(JsonNode body) {
        Long siteId=number(body,"siteId");site(siteId,true);ObjectNode fields=documentFields(siteId,body);
        WebsiteDocumentDO row;
        if(body.hasNonNull("id")){row=document(siteId,number(body,"id"),true);version(row.getVersion(),version(body));row.setVersion(row.getVersion()+1).setDraftJson(fields.toString());documents.updateById(row);}
        else {if(documents.selectCount(documents.scope(siteId))>=300)throw exception(OPERATION_LIMIT);row=new WebsiteDocumentDO().setSiteId(siteId).setVersion(1).setDraftJson(fields.toString());row.setTenantId(tenant());documents.insert(row);}
        audit(siteId,"save-document",row.getId().toString());return view(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public ObjectNode publishDocument(JsonNode body) {
        Long siteId=number(body,"siteId");site(siteId,true);var row=document(siteId,number(body,"id"),true);version(row.getVersion(),version(body));
        ObjectNode fields=documentFields(siteId,parse(row.getDraftJson()));if(expired(fields))throw exception(OPERATION_DOCUMENT_EXPIRED);
        approval.requireApproved(siteId,"document",row.getId().toString(),row.getVersion());
        fields.put("publishedAt",now());row.setPublishedJson(fields.toString());documents.updateById(row);approval.published(siteId,"document",row.getId().toString(),row.getVersion());audit(siteId,"publish-document",row.getId().toString());return view(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public ObjectNode offlineDocument(JsonNode body) {
        Long siteId=number(body,"siteId");site(siteId,true);var row=document(siteId,number(body,"id"),true);version(row.getVersion(),version(body));row.setPublishedJson(null).setVersion(row.getVersion()+1);documents.updateById(row);audit(siteId,"offline-document",row.getId().toString());return view(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public boolean deleteDocument(JsonNode body) {
        Long siteId=number(body,"siteId");site(siteId,true);var row=document(siteId,number(body,"id"),true);version(row.getVersion(),version(body));if(row.getPublishedJson()!=null)throw exception(OPERATION_OFFLINE_REQUIRED);documents.deleteById(row.getId());audit(siteId,"delete-document",row.getId().toString());return true;
    }
    private static boolean expired(JsonNode fields) { String value=fields.path("expiresAt").asText("");return !value.isBlank()&&date(value,false).isBefore(LocalDate.now()); }
    public List<ObjectNode> publicDocuments(Long siteId) {
        site(siteId,false);return documents.documents(siteId).stream().filter(d->d.getPublishedJson()!=null).filter(d->!expired(parse(d.getPublishedJson()))).map(d->{ObjectNode p=parse(d.getPublishedJson());p.remove(List.of("assetId","owner"));return p.put("id",d.getId()).put("url","/app-api/seo/operations/document/download?id="+d.getId()+"&siteId="+siteId);}).toList();
    }
    public byte[] download(Long siteId,Long id) {
        site(siteId,false);var row=document(siteId,id,false);if(row.getPublishedJson()==null || expired(parse(row.getPublishedJson())))throw exception(OPERATION_NOT_FOUND);
        var pdf=file(siteId,number(parse(row.getPublishedJson()),"assetId"));byte[] bytes=bytes(siteId,pdf.getId());if(!hash(bytes).equals(pdf.getSha256()))throw exception(OPERATION_NOT_FOUND);return bytes;
    }
    /** Authenticated reviewers need the original PDF before approving a document. */
    public byte[] reviewFile(Long siteId,Long id) {
        site(siteId,false);var file=file(siteId,id);byte[] bytes=bytes(siteId,id);
        if(!hash(bytes).equals(file.getSha256()))throw exception(OPERATION_NOT_FOUND);
        return bytes;
    }
    @Transactional(rollbackFor=Exception.class)
    public boolean deleteFile(JsonNode body) {
        Long siteId=number(body,"siteId"),id=number(body,"id");site(siteId,true);file(siteId,id);
        // Document changes take the same site lock, preventing a new reference during deletion.
        for(var document:documents.documents(siteId)) {
            if(number(parse(document.getDraftJson()),"assetId")==id
                    || document.getPublishedJson()!=null&&number(parse(document.getPublishedJson()),"assetId")==id)
                throw exception(OPERATION_FILE_REFERENCED);
        }
        if(files.deleteUnused(tenant(),siteId,id)!=1)throw exception(OPERATION_VERSION_CONFLICT);
        audit(siteId,"delete-unused-private-file",id.toString());return true;
    }
    private record Target(int version,String title) {}
    private Target target(Long siteId,String kind,String id) {
        if("catalog".equals(kind)){if(!"catalog".equals(id))throw exception(OPERATION_INVALID);var page=pages.getDraft(key(siteId));return new Target(page.getVersion(),"产品目录");}
        long recordId;try{recordId=Long.parseLong(id);}catch(RuntimeException error){throw exception(OPERATION_INVALID);}
        if("document".equals(kind)){var d=document(siteId,recordId,false);return new Target(d.getVersion(),parse(d.getDraftJson()).path("title").asText());}
        if("article".equals(kind)){var a=articles.selectByIdForTenant(recordId);if(a==null || !siteId.equals(a.getSiteId()))throw exception(OPERATION_NOT_FOUND);return new Target(a.getVersion(),a.getTitle());}
        throw exception(OPERATION_INVALID);
    }
    @Transactional(rollbackFor=Exception.class)
    public ObjectNode submitReview(JsonNode body) {
        Long siteId=number(body,"siteId");site(siteId,true);String kind=text(body,"kind",20,true),id=body.path("targetId").asText("");Target target=target(siteId,kind,id);version(target.version(),version(body));
        var matching=records.selectList(records.scope(siteId).eq(WebsiteOperationRecordDO::getKind,"review").eq(WebsiteOperationRecordDO::getTargetKind,kind).eq(WebsiteOperationRecordDO::getTargetKey,id).eq(WebsiteOperationRecordDO::getContentVersion,target.version()).in(WebsiteOperationRecordDO::getStatus,List.of("pending","approved")));
        if(!matching.isEmpty())return view(matching.get(0));
        var row=insert(siteId,"review",object().put("title",target.title()).put("submittedBy",actor()).put("submittedAt",now()).put("note",""));
        row.setTargetKind(kind).setTargetKey(id).setContentVersion(target.version()).setStatus("pending");records.updateById(row);audit(siteId,"submit-review",row.getId().toString());return view(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public ObjectNode decideReview(JsonNode body) {
        Long siteId=number(body,"siteId");site(siteId,true);var row=record(siteId,number(body,"id"),"review");version(row.getVersion(),version(body));if(!"pending".equals(row.getStatus()))throw exception(OPERATION_VERSION_CONFLICT);
        Target target=target(siteId,row.getTargetKind(),row.getTargetKey());version(target.version(),row.getContentVersion());
        String status=text(body,"status",20,true);if(!Set.of("approved","rejected").contains(status))throw exception(OPERATION_INVALID);
        String note=text(body,"note",1000,"rejected".equals(status));row.setStatus(status).setVersion(row.getVersion()+1).setPayloadJson(parse(row.getPayloadJson()).put("note",note).put("reviewedBy",actor()).put("reviewedAt",now()).toString());records.updateById(row);audit(siteId,status,row.getId().toString());return view(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public ObjectNode saveTask(JsonNode body) {
        Long siteId=number(body,"siteId");site(siteId,true);ObjectNode payload=object().put("title",text(body,"title",180,true)).put("owner",text(body,"owner",100,true)).put("due",text(body,"due",10,true)).put("status",text(body,"status",10,true));
        date(payload.path("due").asText(),true);if(!Set.of("open","done").contains(payload.path("status").asText()))throw exception(OPERATION_INVALID);
        WebsiteOperationRecordDO row;if(body.hasNonNull("id")){row=record(siteId,number(body,"id"),"task");version(row.getVersion(),version(body));row.setVersion(row.getVersion()+1).setPayloadJson(payload.toString());records.updateById(row);}else{if(records.selectCount(records.scope(siteId).eq(WebsiteOperationRecordDO::getKind,"task"))>=300)throw exception(OPERATION_LIMIT);row=insert(siteId,"task",payload);}
        audit(siteId,"save-task",row.getId().toString());return view(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public boolean deleteTask(JsonNode body) { Long siteId=number(body,"siteId");site(siteId,true);var row=record(siteId,number(body,"id"),"task");version(row.getVersion(),version(body));records.deleteById(row.getId());audit(siteId,"delete-task",row.getId().toString());return true; }
    @Transactional(rollbackFor=Exception.class)
    public ObjectNode check(Long siteId) {
        site(siteId,true);List<Map<String,String>> issues=new ArrayList<>();var catalog=pages.getDraft(key(siteId));
        for(var p:catalog.getContent().path("products")) {
            if(p.path("specs").isEmpty())issues.add(issue("specification",p.path("name").asText(),"规格待供应商确认"));
            String source=p.path("sourceDate").asText("");try{if(LocalDate.parse(source).isBefore(LocalDate.now().minusDays(180)))issues.add(issue("review",p.path("name").asText(),"来源超过180天，需复核"));}catch(RuntimeException ex){issues.add(issue("review",p.path("name").asText(),"来源日期无效"));}
            if(p.path("image").asText("").isBlank())issues.add(issue("image",p.path("name").asText(),"未设置产品图片"));
        }
        for(var d:documents.documents(siteId)){var fields=parse(d.getDraftJson());String title=fields.path("title").asText(),expiry=fields.path("expiresAt").asText("");if(!expiry.isBlank()&&!date(expiry,false).isAfter(LocalDate.now().plusDays(30)))issues.add(issue("document",title,expired(fields)?"资料已过期":"资料将在30天内到期"));if(d.getPublishedJson()==null)issues.add(issue("document",title,"资料尚未发布"));}
        for(var t:records.records(siteId,"task")){var fields=parse(t.getPayloadJson());if("open".equals(fields.path("status").asText())&&!date(fields.path("due").asText(),true).isAfter(LocalDate.now()))issues.add(issue("task",fields.path("title").asText(),"复核任务已到期 · "+fields.path("owner").asText()));}
        ObjectNode result=object().put("at",now());result.set("issues",JsonUtils.parseTree(JsonUtils.toJsonString(issues)));var row=insert(siteId,"check",result);audit(siteId,"content-check",row.getId().toString());return view(row);
    }
    private static Map<String,String> issue(String kind,String target,String message){return Map.of("kind",kind,"target",target,"message",message);}
    private static WebsitePageKeyReqVO key(Long siteId){return new WebsitePageKeyReqVO().setSiteId(siteId).setLocale("en").setPageKey("catalog");}
    private static String hash(byte[] content){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
    // Canonical ordering makes checksum independent of the JSON client's property ordering.
    private static JsonNode canonical(JsonNode value){if(value.isObject()){ObjectNode result=object();List<String> names=new ArrayList<>();value.fieldNames().forEachRemaining(names::add);Collections.sort(names);for(String name:names)result.set(name,canonical(value.get(name)));return result;}if(value.isArray()){var result=JsonUtils.parseTree("[]").deepCopy();for(JsonNode item:value)((com.fasterxml.jackson.databind.node.ArrayNode)result).add(canonical(item));return result;}return value;}
    private static String checksum(JsonNode payload){return hash(canonical(payload).toString().getBytes(StandardCharsets.UTF_8));}
    public Map<String,Object> backup(Long siteId) {
        site(siteId,false);ObjectNode payload=object().put("tenantId",tenant()).put("siteId",siteId).put("createdAt",now());payload.set("catalog",pages.getDraft(key(siteId)).getContent());
        var ids=articles.selectList(new LambdaQueryWrapper<WebsiteBlogArticleDO>().eq(WebsiteBlogArticleDO::getTenantId,tenant()).eq(WebsiteBlogArticleDO::getSiteId,siteId).orderByAsc(WebsiteBlogArticleDO::getId));
        payload.set("articles",JsonUtils.parseTree(JsonUtils.toJsonString(ids.stream().map(a->blogs.getArticle(a.getId())).toList())));
        payload.set("documents",JsonUtils.parseTree(JsonUtils.toJsonString(documents.documents(siteId).stream().map(this::view).toList())));
        payload.set("files",JsonUtils.parseTree(JsonUtils.toJsonString(files.selectList(files.scope(siteId)).stream().map(this::view).toList())));
        return Map.of("format","trendz-erp-content-v1","payload",payload,"sha256",checksum(payload));
    }
    private JsonNode validBackup(Long siteId,JsonNode backup){JsonNode p=backup.path("payload");if(!"trendz-erp-content-v1".equals(backup.path("format").asText())||!p.isObject()||p.toString().length()>8*1024*1024||!checksum(p).equals(backup.path("sha256").asText())||number(p,"tenantId")!=tenant()||number(p,"siteId")!=siteId)throw exception(OPERATION_BACKUP_INVALID);for(var f:p.path("files")){var current=file(siteId,number(f,"id"));if(!current.getSha256().equals(f.path("sha256").asText())||!hash(bytes(siteId,current.getId())).equals(current.getSha256()))throw exception(OPERATION_BACKUP_INVALID);}return p;}
    public Map<String,Object> checkBackup(JsonNode body){Long siteId=number(body,"siteId");site(siteId,false);var p=validBackup(siteId,body.path("backup"));return Map.of("products",p.path("catalog").path("products").size(),"articles",p.path("articles"),"documents",p.path("documents"),"documentRows",p.path("documents"),"missingAssets",List.of(),"files",p.path("files").size());}
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> restore(JsonNode body) {
        Long siteId=number(body,"siteId");site(siteId,true);JsonNode p=validBackup(siteId,body.path("backup"));String kind=text(body,"kind",20,true);int expected=version(body);
        if("catalog".equals(kind)){var request=new WebsitePageSaveReqVO();request.setSiteId(siteId);request.setPageKey("catalog");request.setLocale("en");request.setExpectedVersion(expected);request.setContent(p.path("catalog"));pages.saveDraft(request);}
        else if("document".equals(kind)){long id=number(body,"id");JsonNode old=find(p.path("documents"),id);var current=document(siteId,id,true);version(current.getVersion(),expected);current.setDraftJson(documentFields(siteId,old).toString()).setVersion(current.getVersion()+1);documents.updateById(current);}
        else if("article".equals(kind)){long id=number(body,"id");JsonNode old=find(p.path("articles"),id);Target t=target(siteId,kind,Long.toString(id));version(t.version(),expected);ObjectNode allowed=articleSaveFields(old);allowed.put("version",expected);blogs.updateArticle(JsonUtils.parseObject(allowed.toString(),WebsiteBlogArticleSaveReqVO.class));}
        else throw exception(OPERATION_INVALID);
        audit(siteId,"restore-draft",kind);return Map.of("restoredTo","draft","publishedChanged",false);
    }
    private static JsonNode find(JsonNode list,long id){for(var item:list)if(item.path("id").asLong()==id)return item;throw exception(OPERATION_BACKUP_INVALID);}
    private static ObjectNode articleSaveFields(JsonNode old) {
        ObjectNode result=object();
        for(String field:List.of("id","siteId","locale","slug","legacyPath","title","titleLines","category","label","summary","coverImageUrl","coverImageAlt","heroImageUrl","visible","publishedAt","sortOrder","seoTitle","seoDescription"))if(old.has(field))result.set(field,old.get(field));
        var sections=result.putArray("sections");
        for(JsonNode section:old.path("sections")){ObjectNode clean=object();for(String field:List.of("id","title","paragraphs","blocks"))if(section.has(field))clean.set(field,section.get(field));sections.add(clean);}
        return result;
    }
}
