package cn.iocoder.yudao.module.seo.service.analysis;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisCompareRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRerunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoContentSnapshotReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoKeywordAnalysisRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoKeywordRuleRespVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.analysis.SeoAnalysisDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.analysis.SeoKeywordAnalysisDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.analysis.SeoKeywordAnalysisItemDO;
import cn.iocoder.yudao.module.seo.dal.mysql.analysis.SeoAnalysisMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.analysis.SeoKeywordAnalysisItemMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.analysis.SeoKeywordAnalysisMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.metadata.SeoMetadataMapper;
import cn.iocoder.yudao.module.seo.enums.SeoAnalysisSourceTypeEnum;
import cn.iocoder.yudao.module.seo.enums.SeoAnalysisStatusEnum;
import cn.iocoder.yudao.module.seo.enums.SeoEntityTypeEnum;
import cn.iocoder.yudao.module.seo.enums.SeoKeywordTypeEnum;
import cn.iocoder.yudao.module.seo.service.SeoLocaleUtils;
import cn.iocoder.yudao.module.seo.service.analysis.engine.SeoKeywordAnalysisEngine;
import cn.iocoder.yudao.module.seo.service.analysis.lexical.SeoTextNormalizer;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoKeywordEvaluation;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoKeywordRuleResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_COMPARISON_MISMATCH;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_KEYWORD_DUPLICATE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_KEYWORD_INVALID;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_NOT_EXISTS;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_SOURCE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ENTITY_TYPE_INVALID;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.KEYWORD_ANALYSIS_NOT_EXISTS;

@Service
@Validated
public class SeoAnalysisServiceImpl implements SeoAnalysisService {

    @Resource
    private SeoAnalysisMapper analysisMapper;
    @Resource
    private SeoKeywordAnalysisMapper keywordAnalysisMapper;
    @Resource
    private SeoKeywordAnalysisItemMapper keywordAnalysisItemMapper;
    @Resource
    private SeoMetadataMapper metadataMapper;
    @Resource
    private SeoContentSnapshotFactory snapshotFactory;
    @Resource
    private SeoKeywordAnalysisEngine analysisEngine;
    @Resource
    private SeoTextNormalizer normalizer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long runAnalysis(SeoAnalysisRunReqVO reqVO) {
        return runAnalysis(reqVO, null);
    }

    private Long runAnalysis(SeoAnalysisRunReqVO reqVO, Long forcedPreviousAnalysisId) {
        validateIdentity(reqVO);
        reqVO.setLocale(SeoLocaleUtils.normalize(reqVO.getLocale()));
        SeoResolvedContent resolved = snapshotFactory.resolve(reqVO);
        List<KeywordInput> keywords = normalizeKeywords(resolved.focusKeyphrase(), resolved.relatedKeyphrases());
        Map<String, Object> inputSnapshot = createInputSnapshot(resolved.snapshot(), keywords);
        String contentHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(inputSnapshot));

        SeoAnalysisDO existing = analysisMapper.selectByIdempotencyKey(reqVO.getIdempotencyKey());
        if (existing != null) {
            if (!Objects.equals(existing.getContentHash(), contentHash)) {
                throw exception(ANALYSIS_IDEMPOTENCY_CONFLICT);
            }
            return existing.getId();
        }

        Long previousAnalysisId = forcedPreviousAnalysisId != null ? forcedPreviousAnalysisId
                : findLatestAnalysisId(reqVO);
        SeoAnalysisDO analysis = createAnalysis(reqVO, resolved, inputSnapshot, contentHash, previousAnalysisId);
        analysisMapper.insert(analysis);

        List<SeoKeywordEvaluation> evaluations = new ArrayList<>();
        boolean hasFailedKeyword = false;
        for (KeywordInput keyword : keywords) {
            try {
                SeoKeywordEvaluation evaluation = analysisEngine.analyze(
                        keyword.keyword(), keyword.type(), keyword.sort(), resolved.snapshot());
                persistKeyword(analysis.getId(), evaluation);
                evaluations.add(evaluation);
            } catch (RuntimeException ex) {
                hasFailedKeyword = true;
                persistFailedKeyword(analysis.getId(), keyword, ex);
            }
        }
        finishAnalysis(analysis, evaluations, hasFailedKeyword);
        analysisMapper.updateById(analysis);
        if (resolved.metadataId() != null) {
            metadataMapper.updateLatestAnalysisId(resolved.metadataId(), analysis.getId(), currentTenantId(),
                    Objects.toString(SecurityFrameworkUtils.getLoginUserId(), ""));
        }
        return analysis.getId();
    }

    @Override
    public SeoAnalysisRespVO getAnalysis(Long id) {
        SeoAnalysisDO analysis = getRequiredAnalysis(id);
        SeoAnalysisRespVO result = BeanUtils.toBean(analysis, SeoAnalysisRespVO.class);
        result.setKeywords(getKeywords(id));
        return result;
    }

    @Override
    public List<SeoKeywordAnalysisRespVO> getKeywords(Long analysisId) {
        getRequiredAnalysis(analysisId);
        return keywordAnalysisMapper.selectListByAnalysisId(analysisId).stream()
                .map(keyword -> toKeywordResp(keyword, false))
                .toList();
    }

    @Override
    public SeoKeywordAnalysisRespVO getKeyword(Long analysisId, Long keywordAnalysisId) {
        getRequiredAnalysis(analysisId);
        SeoKeywordAnalysisDO keyword = keywordAnalysisMapper.selectByIdAndAnalysisId(keywordAnalysisId, analysisId);
        if (keyword == null) {
            throw exception(KEYWORD_ANALYSIS_NOT_EXISTS);
        }
        return toKeywordResp(keyword, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long rerunAnalysis(Long id, SeoAnalysisRerunReqVO reqVO) {
        SeoAnalysisDO previous = getRequiredAnalysis(id);
        List<SeoKeywordAnalysisDO> previousKeywords = keywordAnalysisMapper.selectListByAnalysisId(id);
        SeoAnalysisRunReqVO runReq = new SeoAnalysisRunReqVO();
        runReq.setSiteId(previous.getSiteId());
        runReq.setEntityType(previous.getEntityType());
        runReq.setEntityId(previous.getEntityId());
        runReq.setLocale(previous.getLocale());
        runReq.setSourceType(previous.getSourceType());
        runReq.setSourceId(previous.getSourceId());
        runReq.setIdempotencyKey(reqVO.getIdempotencyKey());
        runReq.setFocusKeyphrase(previousKeywords.stream()
                .filter(keyword -> SeoKeywordTypeEnum.FOCUS.getCode().equals(keyword.getKeywordType()))
                .map(SeoKeywordAnalysisDO::getKeyword).findFirst().orElse(previous.getFocusKeyphrase()));
        runReq.setRelatedKeyphrases(previousKeywords.stream()
                .filter(keyword -> SeoKeywordTypeEnum.RELATED.getCode().equals(keyword.getKeywordType()))
                .map(SeoKeywordAnalysisDO::getKeyword).toList());
        if (SeoAnalysisSourceTypeEnum.MANUAL.getCode().equals(previous.getSourceType())) {
            runReq.setContent(JsonUtils.convertObject(previous.getInputSnapshot().get("content"),
                    SeoContentSnapshotReqVO.class));
        }
        return runAnalysis(runReq, id);
    }

    @Override
    public SeoAnalysisCompareRespVO compareAnalysis(Long currentAnalysisId, Long previousAnalysisId) {
        SeoAnalysisDO current = getRequiredAnalysis(currentAnalysisId);
        Long resolvedPreviousId = previousAnalysisId != null ? previousAnalysisId : current.getPreviousAnalysisId();
        if (resolvedPreviousId == null) {
            throw exception(ANALYSIS_NOT_EXISTS);
        }
        SeoAnalysisDO previous = getRequiredAnalysis(resolvedPreviousId);
        validateComparable(current, previous);

        List<SeoKeywordAnalysisDO> currentKeywords = keywordAnalysisMapper.selectListByAnalysisId(currentAnalysisId);
        List<SeoKeywordAnalysisDO> previousKeywords = keywordAnalysisMapper.selectListByAnalysisId(resolvedPreviousId);
        Map<String, SeoKeywordAnalysisDO> currentByKey = indexKeywords(currentKeywords);
        Map<String, SeoKeywordAnalysisDO> previousByKey = indexKeywords(previousKeywords);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(currentByKey.keySet());
        keys.addAll(previousByKey.keySet());

        SeoAnalysisCompareRespVO response = new SeoAnalysisCompareRespVO();
        response.setCurrentAnalysisId(currentAnalysisId);
        response.setPreviousAnalysisId(resolvedPreviousId);
        response.setKeywords(keys.stream().map(key -> compareKeyword(
                currentByKey.get(key), previousByKey.get(key))).toList());
        return response;
    }

    private SeoAnalysisDO createAnalysis(SeoAnalysisRunReqVO reqVO, SeoResolvedContent resolved,
                                         Map<String, Object> inputSnapshot, String contentHash,
                                         Long previousAnalysisId) {
        SeoAnalysisDO analysis = new SeoAnalysisDO();
        analysis.setSiteId(reqVO.getSiteId());
        analysis.setSourceType(reqVO.getSourceType());
        analysis.setSourceId(resolved.metadataId() != null ? resolved.metadataId() : reqVO.getSourceId());
        analysis.setEntityType(reqVO.getEntityType());
        analysis.setEntityId(reqVO.getEntityId());
        analysis.setLocale(reqVO.getLocale());
        analysis.setFocusKeyphrase(resolved.focusKeyphrase().trim());
        analysis.setInputSnapshot(inputSnapshot);
        analysis.setContentHash(contentHash);
        analysis.setIdempotencyKey(reqVO.getIdempotencyKey());
        analysis.setPreviousAnalysisId(previousAnalysisId);
        analysis.setEngineVersion(analysisEngine.getEngineVersion());
        analysis.setRuleProfileVersion(analysisEngine.getRuleProfileVersion());
        analysis.setDictionaryVersion("");
        analysis.setAnalysisStatus(SeoAnalysisStatusEnum.RUNNING.getCode());
        analysis.setTenantId(currentTenantId());
        return analysis;
    }

    private void persistKeyword(Long analysisId, SeoKeywordEvaluation evaluation) {
        SeoKeywordAnalysisDO keyword = BeanUtils.toBean(evaluation, SeoKeywordAnalysisDO.class);
        keyword.setId(null);
        keyword.setAnalysisId(analysisId);
        keyword.setTenantId(currentTenantId());
        keywordAnalysisMapper.insert(keyword);
        for (SeoKeywordRuleResult result : evaluation.getItems()) {
            SeoKeywordAnalysisItemDO item = BeanUtils.toBean(result, SeoKeywordAnalysisItemDO.class);
            item.setId(null);
            item.setKeywordAnalysisId(keyword.getId());
            item.setTenantId(currentTenantId());
            keywordAnalysisItemMapper.insert(item);
        }
    }

    private void persistFailedKeyword(Long analysisId, KeywordInput input, RuntimeException ex) {
        SeoKeywordAnalysisDO keyword = new SeoKeywordAnalysisDO();
        keyword.setAnalysisId(analysisId);
        keyword.setKeywordType(input.type());
        keyword.setKeyword(input.keyword());
        keyword.setNormalizedKeyword(input.normalized());
        keyword.setSort(input.sort());
        keyword.setConfidencePercent(0);
        keyword.setAnalysisStatus(SeoAnalysisStatusEnum.FAILED.getCode());
        keyword.setExactMatchCount(0);
        keyword.setVariantMatchCount(0);
        keyword.setMatchedLocations(List.of());
        keyword.setDictionaryVersion("");
        keyword.setTenantId(currentTenantId());
        keywordAnalysisMapper.insert(keyword);

        SeoKeywordAnalysisItemDO item = new SeoKeywordAnalysisItemDO();
        item.setKeywordAnalysisId(keyword.getId());
        item.setRuleCode("KW_ANALYSIS_FAILURE");
        item.setDimension("SYSTEM");
        item.setSeverity("HIGH");
        item.setStatus("NOT_COMPLETED");
        item.setEvidence(Map.of());
        item.setReason("该关键词分析未完成：" + safeMessage(ex));
        item.setRecommendation("保留当前内容并重试；其他关键词的已完成结果不受影响");
        item.setSort(9999);
        item.setTenantId(currentTenantId());
        keywordAnalysisItemMapper.insert(item);
    }

    private void finishAnalysis(SeoAnalysisDO analysis, List<SeoKeywordEvaluation> evaluations,
                                boolean hasFailedKeyword) {
        SeoKeywordEvaluation focus = evaluations.stream()
                .filter(value -> SeoKeywordTypeEnum.FOCUS.getCode().equals(value.getKeywordType()))
                .findFirst().orElse(null);
        Collection<SeoKeywordEvaluation> scoreSource = focus == null ? evaluations : List.of(focus);
        analysis.setOverallRelevancePercent(average(scoreSource, SeoKeywordEvaluation::getRelevancePercent));
        analysis.setConfidencePercent(average(evaluations, SeoKeywordEvaluation::getConfidencePercent));
        analysis.setTotalScore(analysis.getOverallRelevancePercent());
        analysis.setDictionaryVersion(evaluations.stream().map(SeoKeywordEvaluation::getDictionaryVersion)
                .filter(Objects::nonNull).findFirst().orElse(""));
        analysis.setSemanticModelVersion(evaluations.stream().map(SeoKeywordEvaluation::getSemanticModelVersion)
                .filter(Objects::nonNull).findFirst().orElse(null));
        boolean hasPartial = evaluations.stream().anyMatch(value ->
                SeoAnalysisStatusEnum.PARTIAL.getCode().equals(value.getAnalysisStatus()));
        if (evaluations.isEmpty()) {
            analysis.setAnalysisStatus(SeoAnalysisStatusEnum.FAILED.getCode());
            analysis.setFailureCode("ALL_KEYWORDS_FAILED");
            analysis.setFailureMessage("所有关键词分析均未完成");
        } else if (hasPartial || hasFailedKeyword) {
            analysis.setAnalysisStatus(SeoAnalysisStatusEnum.PARTIAL.getCode());
            if (hasFailedKeyword) {
                analysis.setFailureCode("KEYWORD_PARTIAL_FAILURE");
                analysis.setFailureMessage("部分关键词分析未完成，已完成结果仍可查看");
            }
        } else {
            analysis.setAnalysisStatus(SeoAnalysisStatusEnum.SUCCEEDED.getCode());
        }
    }

    private SeoKeywordAnalysisRespVO toKeywordResp(SeoKeywordAnalysisDO keyword, boolean includeItems) {
        SeoKeywordAnalysisRespVO result = BeanUtils.toBean(keyword, SeoKeywordAnalysisRespVO.class);
        List<SeoKeywordAnalysisItemDO> items = keywordAnalysisItemMapper
                .selectListByKeywordAnalysisId(keyword.getId());
        result.setSuggestionCount((int) items.stream()
                .filter(item -> StrUtil.isNotBlank(item.getRecommendation())).count());
        result.setItems(includeItems ? BeanUtils.toBean(items, SeoKeywordRuleRespVO.class) : null);
        return result;
    }

    private SeoAnalysisCompareRespVO.KeywordComparison compareKeyword(SeoKeywordAnalysisDO current,
                                                                       SeoKeywordAnalysisDO previous) {
        SeoKeywordAnalysisDO reference = current != null ? current : previous;
        SeoAnalysisCompareRespVO.KeywordComparison result = new SeoAnalysisCompareRespVO.KeywordComparison();
        result.setKeywordType(reference.getKeywordType());
        result.setKeyword(reference.getKeyword());
        result.setNormalizedKeyword(reference.getNormalizedKeyword());
        result.setCurrentPercent(current == null ? null : current.getRelevancePercent());
        result.setPreviousPercent(previous == null ? null : previous.getRelevancePercent());
        result.setDeltaPercent(current == null || previous == null
                || current.getRelevancePercent() == null || previous.getRelevancePercent() == null
                ? null : current.getRelevancePercent() - previous.getRelevancePercent());
        result.setChangeType(changeType(current, previous, result.getDeltaPercent()));
        Set<String> currentRules = issueRuleCodes(current);
        Set<String> previousRules = issueRuleCodes(previous);
        result.setResolvedRuleCodes(previousRules.stream().filter(code -> !currentRules.contains(code)).toList());
        result.setNewRuleCodes(currentRules.stream().filter(code -> !previousRules.contains(code)).toList());
        return result;
    }

    private Set<String> issueRuleCodes(SeoKeywordAnalysisDO keyword) {
        if (keyword == null) {
            return Set.of();
        }
        return keywordAnalysisItemMapper.selectListByKeywordAnalysisId(keyword.getId()).stream()
                .filter(item -> "ISSUE".equals(item.getStatus()))
                .map(SeoKeywordAnalysisItemDO::getRuleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String changeType(SeoKeywordAnalysisDO current, SeoKeywordAnalysisDO previous, Integer delta) {
        if (previous == null) {
            return "ADDED";
        }
        if (current == null) {
            return "REMOVED";
        }
        if (delta == null || delta == 0) {
            return "UNCHANGED";
        }
        return delta > 0 ? "IMPROVED" : "REGRESSED";
    }

    private Map<String, SeoKeywordAnalysisDO> indexKeywords(List<SeoKeywordAnalysisDO> keywords) {
        return keywords.stream().collect(Collectors.toMap(this::keywordKey, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
    }

    private String keywordKey(SeoKeywordAnalysisDO keyword) {
        return keyword.getKeywordType() + "|" + keyword.getNormalizedKeyword();
    }

    private void validateComparable(SeoAnalysisDO current, SeoAnalysisDO previous) {
        if (!Objects.equals(current.getSiteId(), previous.getSiteId())
                || !Objects.equals(current.getEntityType(), previous.getEntityType())
                || !Objects.equals(current.getEntityId(), previous.getEntityId())
                || !Objects.equals(current.getLocale(), previous.getLocale())) {
            throw exception(ANALYSIS_COMPARISON_MISMATCH);
        }
    }

    private List<KeywordInput> normalizeKeywords(String focus, List<String> related) {
        List<KeywordInput> result = new ArrayList<>();
        Set<String> normalizedValues = new LinkedHashSet<>();
        addKeyword(result, normalizedValues, focus, SeoKeywordTypeEnum.FOCUS.getCode(), 0);
        int sort = 1;
        for (String keyword : related == null ? List.<String>of() : related) {
            addKeyword(result, normalizedValues, keyword, SeoKeywordTypeEnum.RELATED.getCode(), sort++);
        }
        return result;
    }

    private void addKeyword(List<KeywordInput> target, Set<String> normalizedValues,
                            String keyword, String type, int sort) {
        String normalized = normalizer.normalize(keyword);
        if (normalized.isBlank()) {
            throw exception(ANALYSIS_KEYWORD_INVALID);
        }
        if (!normalizedValues.add(normalized)) {
            throw exception(ANALYSIS_KEYWORD_DUPLICATE);
        }
        target.add(new KeywordInput(keyword.trim(), normalized, type, sort));
    }

    private Map<String, Object> createInputSnapshot(SeoContentSnapshot content, List<KeywordInput> keywords) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("content", content);
        input.put("keywords", keywords.stream().map(keyword -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("keyword", keyword.keyword());
            value.put("normalizedKeyword", keyword.normalized());
            value.put("keywordType", keyword.type());
            value.put("sort", keyword.sort());
            return value;
        }).toList());
        input.put("engineVersion", analysisEngine.getEngineVersion());
        input.put("ruleProfileVersion", analysisEngine.getRuleProfileVersion());
        return input;
    }

    private Long findLatestAnalysisId(SeoAnalysisRunReqVO reqVO) {
        return analysisMapper.selectEntityHistory(reqVO.getSiteId(), reqVO.getEntityType(),
                        reqVO.getEntityId(), reqVO.getLocale()).stream()
                .map(SeoAnalysisDO::getId).findFirst().orElse(null);
    }

    private SeoAnalysisDO getRequiredAnalysis(Long id) {
        SeoAnalysisDO analysis = analysisMapper.selectByIdForTenant(id);
        if (analysis == null) {
            throw exception(ANALYSIS_NOT_EXISTS);
        }
        return analysis;
    }

    private void validateIdentity(SeoAnalysisRunReqVO reqVO) {
        if (!SeoEntityTypeEnum.isValid(reqVO.getEntityType())) {
            throw exception(ENTITY_TYPE_INVALID);
        }
        if (!SeoAnalysisSourceTypeEnum.isValid(reqVO.getSourceType())) {
            throw exception(ANALYSIS_SOURCE_NOT_SUPPORTED);
        }
    }

    private static int average(Collection<SeoKeywordEvaluation> values,
                               Function<SeoKeywordEvaluation, Integer> extractor) {
        return (int) Math.round(values.stream().map(extractor).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average().orElse(0));
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private static Long currentTenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private record KeywordInput(String keyword, String normalized, String type, int sort) {
    }

}
