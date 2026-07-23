package cn.iocoder.yudao.module.seo.service.analysis;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoContentSnapshotReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.metadata.SeoMetadataDO;
import cn.iocoder.yudao.module.seo.dal.mysql.metadata.SeoMetadataMapper;
import cn.iocoder.yudao.module.seo.enums.SeoAnalysisSourceTypeEnum;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_CONTENT_REQUIRED;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_SOURCE_NOT_SUPPORTED;

@Component
public class SeoContentSnapshotFactory {

    @Resource
    private SeoMetadataMapper metadataMapper;
    @Resource
    private ObjectProvider<ProductSpuApi> productSpuApiProvider;

    public SeoResolvedContent resolve(SeoAnalysisRunReqVO reqVO) {
        if (SeoAnalysisSourceTypeEnum.MANUAL.getCode().equals(reqVO.getSourceType())) {
            SeoContentSnapshot snapshot = fromManual(reqVO.getContent());
            ensureContent(snapshot);
            return new SeoResolvedContent(snapshot, null, reqVO.getFocusKeyphrase(),
                    defaultList(reqVO.getRelatedKeyphrases()));
        }
        if (SeoAnalysisSourceTypeEnum.DOCUMENT.getCode().equals(reqVO.getSourceType())) {
            throw exception(ANALYSIS_SOURCE_NOT_SUPPORTED);
        }
        if (!SeoAnalysisSourceTypeEnum.ENTITY.getCode().equals(reqVO.getSourceType())) {
            throw exception(ANALYSIS_SOURCE_NOT_SUPPORTED);
        }

        SeoMetadataDO metadata = resolveMetadata(reqVO);
        SeoContentSnapshot snapshot = fromMetadata(metadata);
        if ("PRODUCT".equals(reqVO.getEntityType())) {
            ProductSpuApi api = productSpuApiProvider.orderedStream().findFirst().orElse(null);
            if (api != null) {
                ProductSpuRespDTO product = api.getSpu(reqVO.getEntityId()).getCheckedData();
                if (product != null) {
                    mergeProduct(snapshot, product);
                }
            }
        }
        ensureContent(snapshot);
        String focus = StrUtil.isNotBlank(metadata.getFocusKeyphrase())
                ? metadata.getFocusKeyphrase() : reqVO.getFocusKeyphrase();
        List<String> related = metadata.getRelatedKeyphrases() == null
                ? defaultList(reqVO.getRelatedKeyphrases()) : metadata.getRelatedKeyphrases();
        return new SeoResolvedContent(snapshot, metadata.getId(), focus, related);
    }

    private SeoMetadataDO resolveMetadata(SeoAnalysisRunReqVO reqVO) {
        SeoMetadataDO metadata = reqVO.getSourceId() == null
                ? metadataMapper.selectByEntity(reqVO.getSiteId(), reqVO.getEntityType(),
                        reqVO.getEntityId(), reqVO.getLocale())
                : metadataMapper.selectByIdForTenant(reqVO.getSourceId());
        if (metadata == null
                || !Objects.equals(metadata.getSiteId(), reqVO.getSiteId())
                || !Objects.equals(metadata.getEntityType(), reqVO.getEntityType())
                || !Objects.equals(metadata.getEntityId(), reqVO.getEntityId())
                || !Objects.equals(metadata.getLocale(), reqVO.getLocale())) {
            throw exception(ANALYSIS_CONTENT_REQUIRED);
        }
        return metadata;
    }

    private SeoContentSnapshot fromMetadata(SeoMetadataDO metadata) {
        SeoContentSnapshot snapshot = new SeoContentSnapshot();
        snapshot.setSeoTitle(metadata.getSeoTitle());
        snapshot.setMetaDescription(metadata.getMetaDescription());
        snapshot.setSlug(pathOf(metadata.getCanonicalUrl()));
        return snapshot;
    }

    private void mergeProduct(SeoContentSnapshot snapshot, ProductSpuRespDTO product) {
        snapshot.setH1(product.getName());
        snapshot.setIntroduction(product.getIntroduction());
        snapshot.setBody(product.getDescription());
        Map<String, String> attributes = new TreeMap<>();
        putIfPresent(attributes, "商品分类", product.getCategoryName());
        putIfPresent(attributes, "站内关键词", product.getKeyword());
        if (product.getDetailConfig() != null) {
            product.getDetailConfig().forEach((key, value) -> {
                if (value != null) {
                    attributes.put("详情配置." + key, value instanceof String
                            ? (String) value : JsonUtils.toJsonString(value));
                }
            });
        }
        snapshot.setAttributes(new LinkedHashMap<>(attributes));
    }

    private SeoContentSnapshot fromManual(SeoContentSnapshotReqVO source) {
        if (source == null) {
            throw exception(ANALYSIS_CONTENT_REQUIRED);
        }
        SeoContentSnapshot snapshot = new SeoContentSnapshot();
        snapshot.setSeoTitle(source.getSeoTitle());
        snapshot.setH1(source.getH1());
        snapshot.setIntroduction(source.getIntroduction());
        snapshot.setMetaDescription(source.getMetaDescription());
        snapshot.setSlug(source.getSlug());
        snapshot.setBody(source.getBody());
        snapshot.setHeadings(cleanList(source.getHeadings()));
        snapshot.setParagraphs(cleanList(source.getParagraphs()));
        snapshot.setAttributes(cleanMap(source.getAttributes()));
        snapshot.setImageAlts(cleanList(source.getImageAlts()));
        return snapshot;
    }

    private static List<String> cleanList(List<String> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        return source.stream().filter(StrUtil::isNotBlank).map(String::trim).toList();
    }

    private static Map<String, String> cleanMap(Map<String, String> source) {
        Map<String, String> result = new TreeMap<>();
        if (source != null) {
            source.forEach((key, value) -> {
                if (StrUtil.isNotBlank(key) && StrUtil.isNotBlank(value)) {
                    result.put(key.trim(), value.trim());
                }
            });
        }
        return new LinkedHashMap<>(result);
    }

    private static String pathOf(String canonicalUrl) {
        if (StrUtil.isBlank(canonicalUrl)) {
            return "";
        }
        try {
            return Objects.toString(URI.create(canonicalUrl).getPath(), "");
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            target.put(key, value.trim());
        }
    }

    private static void ensureContent(SeoContentSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            throw exception(ANALYSIS_CONTENT_REQUIRED);
        }
    }

    private static List<String> defaultList(List<String> values) {
        return values == null ? List.of() : values;
    }

}
