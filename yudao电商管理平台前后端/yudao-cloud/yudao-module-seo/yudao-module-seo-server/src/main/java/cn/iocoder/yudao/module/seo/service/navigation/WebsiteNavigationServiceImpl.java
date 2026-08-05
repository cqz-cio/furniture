package cn.iocoder.yudao.module.seo.service.navigation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.api.category.ProductCategoryApi;
import cn.iocoder.yudao.module.product.api.category.dto.ProductCategoryNavigationNameUpdateReqDTO;
import cn.iocoder.yudao.module.product.api.category.dto.ProductCategoryNavigationRespDTO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationCategoryOptionRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationDraftRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationDraftSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationItemRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationItemSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPreviewTicketReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPreviewTicketRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPublishReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationRestoreReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationRevisionRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationTargetOptionRespVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationItemRespVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationPreviewSessionRespVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationRespVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.navigation.WebsiteNavigationItemDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.navigation.WebsiteNavigationRevisionDO;
import cn.iocoder.yudao.module.seo.dal.mysql.navigation.WebsiteNavigationItemMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.navigation.WebsiteNavigationRevisionMapper;
import cn.iocoder.yudao.module.seo.dal.redis.navigation.WebsiteNavigationPreviewGrant;
import cn.iocoder.yudao.module.seo.dal.redis.navigation.WebsiteNavigationPreviewRedisDAO;
import cn.iocoder.yudao.module.seo.enums.navigation.WebsiteNavigationItemTypeEnum;
import cn.iocoder.yudao.module.seo.enums.navigation.WebsiteNavigationPageKeyEnum;
import cn.iocoder.yudao.module.seo.enums.navigation.WebsiteNavigationRevisionStatusEnum;
import cn.iocoder.yudao.module.seo.enums.navigation.WebsiteNavigationTargetEnum;
import cn.iocoder.yudao.module.seo.enums.navigation.WebsiteNavigationTemplateEnum;
import cn.iocoder.yudao.module.seo.service.SeoLocaleUtils;
import cn.iocoder.yudao.module.seo.service.config.SeoSiteConfigService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.NAVIGATION_CATEGORY_UNAVAILABLE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.NAVIGATION_CONFIG_INVALID;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.NAVIGATION_PREVIEW_EXPIRED;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.NAVIGATION_PREVIEW_ORIGIN_MISMATCH;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.NAVIGATION_REVISION_NOT_EXISTS;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.NAVIGATION_VERSION_CONFLICT;

@Service
@Validated
public class WebsiteNavigationServiceImpl implements WebsiteNavigationService {

    private static final Duration PREVIEW_TICKET_TTL = Duration.ofMinutes(10);
    private static final Duration PREVIEW_SESSION_TTL = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_NAVIGATION_ITEMS = 100;
    private static final int MAX_NAVIGATION_DEPTH = 3;
    private static final String SELF_OPEN_MODE = "_self";
    private static final String BLANK_OPEN_MODE = "_blank";
    private static final String DEFAULT_STYLE_VARIANT = "DEFAULT";
    private static final String SALE_STYLE_VARIANT = "SALE";
    private static final Pattern ITEM_KEY_PATTERN = Pattern.compile("^[A-Z0-9_-]{3,64}$");
    private static final List<OakvedSeedSpec> OAKVED_SEED_ITEMS = List.of(
            oakvedSeed("OAKVED_NEW", "", "FILTER", "FILTER_NEW", "NEW", 10, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_COLLECTIONS", "", "FILTER", "FILTER_COLLECTIONS_ALL", "SHOP BY COLLECTIONS", 20, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM", "", "FILTER", "FILTER_ROOM_BEDROOM", "BEDROOM", 30, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING", "", "FILTER", "FILTER_ROOM_LIVING", "LIVING", 40, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DINING", "", "FILTER", "FILTER_ROOM_DINING", "DINING", 50, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BESPOKE", "", "FILTER", "FILTER_COLLECTION_BESPOKE", "BESPOKE", 60, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DECOR", "", "FILTER", "FILTER_CATEGORY_DECOR", "DECOR", 70, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_SALE", "", "ROUTE", "ROUTE_SALE", "SALE", 80, SALE_STYLE_VARIANT),

            oakvedSeed("OAKVED_COLLECTIONS_CATALOG", "OAKVED_COLLECTIONS", "ROUTE", "ROUTE_CATALOG", "OAKVED catalog", 10, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_COLLECTIONS_SOLSTICE", "OAKVED_COLLECTIONS", "FILTER", "FILTER_COLLECTION_SOLSTICE", "The Solstice", 20, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_COLLECTIONS_HALCYON", "OAKVED_COLLECTIONS", "FILTER", "FILTER_COLLECTION_HALCYON", "Halcyon", 30, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_COLLECTIONS_KINDRED", "OAKVED_COLLECTIONS", "FILTER", "FILTER_COLLECTION_KINDRED", "Kindred", 40, DEFAULT_STYLE_VARIANT),

            oakvedSeed("OAKVED_BEDROOM_CATALOG", "OAKVED_BEDROOM", "ROUTE", "ROUTE_CATALOG", "OAKVED catalog", 10, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_BEDS", "OAKVED_BEDROOM", "FILTER", "FILTER_CATEGORY_BED", "Beds", 20, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_HEADBOARD", "OAKVED_BEDROOM", "FILTER", "FILTER_CATEGORY_HEADBOARD", "Headboard", 30, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_NIGHTSTANDS", "OAKVED_BEDROOM", "FILTER", "FILTER_CATEGORY_NIGHTSTAND", "Nightstands", 40, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_BENCHES", "OAKVED_BEDROOM", "FILTER", "FILTER_CATEGORY_BENCH", "Benches", 50, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_DRESSERS", "OAKVED_BEDROOM", "FILTER", "FILTER_CATEGORY_DRESSER", "Dressers", 60, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_CHAIRS", "OAKVED_BEDROOM", "FILTER", "FILTER_CATEGORY_CHAIR", "Chairs", 70, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_SIDE_TABLES", "OAKVED_BEDROOM", "FILTER", "FILTER_CATEGORY_SIDE_TABLE", "Side Tables", 80, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_FABRIC_CARE", "OAKVED_BEDROOM", "FILTER", "FILTER_GROUP_FABRIC_CARE", "Fabric Care", 90, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_CRAFTSMANSHIP", "OAKVED_BEDROOM", "FILTER", "FILTER_GROUP_MATERIALS_CRAFTSMANSHIP", "Materials & Craftsmanship", 100, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_BEDROOM_SALES", "OAKVED_BEDROOM", "ROUTE", "ROUTE_SALE", "Sales", 110, DEFAULT_STYLE_VARIANT),

            oakvedSeed("OAKVED_LIVING_CATALOG", "OAKVED_LIVING", "ROUTE", "ROUTE_CATALOG", "OAKVED catalog", 10, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_SOFAS", "OAKVED_LIVING", "FILTER", "FILTER_CATEGORY_SOFA", "Sofas", 20, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_TABLES", "OAKVED_LIVING", "FILTER", "FILTER_CATEGORY_TABLE", "Tables", 30, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_CONSOLES", "OAKVED_LIVING", "FILTER", "FILTER_CATEGORY_CONSOLE", "Consoles", 40, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_SIDEBOARDS", "OAKVED_LIVING", "FILTER", "FILTER_CATEGORY_SIDEBOARD", "Sideboards", 50, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_CABINETS", "OAKVED_LIVING", "FILTER", "FILTER_CATEGORY_CABINET", "Cabinets", 60, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_BENCHES", "OAKVED_LIVING", "FILTER", "FILTER_CATEGORY_BENCH", "Benches", 70, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_CHAIRS", "OAKVED_LIVING", "FILTER", "FILTER_CATEGORY_CHAIR", "Chairs", 80, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_STOOLS", "OAKVED_LIVING", "FILTER", "FILTER_CATEGORY_STOOL", "Stools", 90, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_FABRIC_CARE", "OAKVED_LIVING", "FILTER", "FILTER_GROUP_FABRIC_CARE", "Fabric Care", 100, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_CRAFTSMANSHIP", "OAKVED_LIVING", "FILTER", "FILTER_GROUP_MATERIALS_CRAFTSMANSHIP", "Materials & Craftsmanship", 110, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_LIVING_SALES", "OAKVED_LIVING", "ROUTE", "ROUTE_SALE", "Sales", 120, DEFAULT_STYLE_VARIANT),

            oakvedSeed("OAKVED_DINING_CATALOG", "OAKVED_DINING", "ROUTE", "ROUTE_CATALOG", "OAKVED catalog", 10, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DINING_RECTANGULAR_TABLES", "OAKVED_DINING", "FILTER", "FILTER_CATEGORY_RECTANGULAR_TABLE", "Rectangular Tables", 20, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DINING_ROUND_OVAL_TABLES", "OAKVED_DINING", "FILTER", "FILTER_CATEGORY_ROUND_OVAL_TABLE", "Round & Oval Tables", 30, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DINING_BISTRO_TABLES", "OAKVED_DINING", "FILTER", "FILTER_CATEGORY_BISTRO_TABLE", "Bistro Tables", 40, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DINING_FABRIC_CHAIRS", "OAKVED_DINING", "FILTER", "FILTER_CATEGORY_FABRIC_CHAIR", "Fabric Chairs", 50, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DINING_WOOD_WOVEN_CHAIRS", "OAKVED_DINING", "FILTER", "FILTER_CATEGORY_WOOD_WOVEN_CHAIR", "Wood & Woven Chairs", 60, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DINING_BAR_COUNTER_STOOLS", "OAKVED_DINING", "FILTER", "FILTER_CATEGORY_BAR_COUNTER_STOOL", "Bar & Counter Stools", 70, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DINING_UPHOLSTERY_SWATCHES", "OAKVED_DINING", "FILTER", "FILTER_GROUP_UPHOLSTERY_SWATCHES", "Upholstery Swatches", 80, DEFAULT_STYLE_VARIANT),
            oakvedSeed("OAKVED_DINING_SALES", "OAKVED_DINING", "ROUTE", "ROUTE_SALE", "Sales", 90, DEFAULT_STYLE_VARIANT));
    private static final Set<String> OAKVED_PRIMARY_KEYS = OAKVED_SEED_ITEMS.stream()
            .filter(item -> StrUtil.isBlank(item.parentItemKey()))
            .map(OakvedSeedSpec::itemKey)
            .collect(Collectors.toUnmodifiableSet());

    @Resource
    private WebsiteNavigationRevisionMapper revisionMapper;
    @Resource
    private WebsiteNavigationItemMapper itemMapper;
    @Resource
    private ProductCategoryApi productCategoryApi;
    @Resource
    private SeoSiteConfigService siteConfigService;
    @Resource
    private WebsiteNavigationPreviewRedisDAO previewRedisDAO;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebsiteNavigationDraftRespVO getDraft(Long siteId, String locale) {
        String normalizedLocale = SeoLocaleUtils.normalize(locale);
        WebsiteNavigationTemplateEnum template = navigationTemplate(siteId);
        WebsiteNavigationRevisionDO draft = revisionMapper.selectActive(siteId, normalizedLocale,
                WebsiteNavigationRevisionStatusEnum.DRAFT.getCode());
        if (draft == null) {
            draft = createDraft(siteId, normalizedLocale, template);
        }
        ensureDraftTemplate(draft, template);
        return buildDraftResponse(draft, template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<WebsiteNavigationCategoryOptionRespVO> getCategoryOptions(Long siteId, String locale) {
        return getDraft(siteId, locale).getCategoryOptions();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(WebsiteNavigationDraftSaveReqVO reqVO) {
        WebsiteNavigationRevisionDO draft = getRequiredDraft(reqVO.getRevisionId());
        String normalizedLocale = SeoLocaleUtils.normalize(reqVO.getLocale());
        if (!Objects.equals(draft.getSiteId(), reqVO.getSiteId())
                || !Objects.equals(draft.getLocale(), normalizedLocale)) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        WebsiteNavigationTemplateEnum template = navigationTemplate(draft.getSiteId());
        Map<Long, ProductCategoryNavigationRespDTO> categoryMap = loadCategoryMap();
        List<WebsiteNavigationItemDO> items = validateAndConvertItems(
                reqVO.getItems(), draft.getId(), template, categoryMap);
        int affected = revisionMapper.bumpDraftVersionAtomic(draft.getId(), reqVO.getVersion(),
                currentTenantId(), currentUpdater());
        if (affected == 0) {
            classifyAtomicFailure(draft.getId());
        }
        if (template == WebsiteNavigationTemplateEnum.VANZ_B2B) {
            syncCategoryNames(reqVO.getItems(), categoryMap);
        }
        itemMapper.deleteByRevisionId(draft.getId());
        insertItems(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(WebsiteNavigationPublishReqVO reqVO) {
        WebsiteNavigationRevisionDO draft = getRequiredDraft(reqVO.getRevisionId());
        if (!Objects.equals(draft.getVersion(), reqVO.getVersion())) {
            throw exception(NAVIGATION_VERSION_CONFLICT);
        }
        validatePublishableItems(itemMapper.selectListByRevisionId(draft.getId()), draft.getSiteId());
        String updater = currentUpdater();
        revisionMapper.archivePublished(draft.getSiteId(), draft.getLocale(), currentTenantId(), updater);
        int affected = revisionMapper.publishDraftAtomic(draft.getId(), reqVO.getVersion(),
                currentTenantId(), updater);
        if (affected == 0) {
            classifyAtomicFailure(draft.getId());
        }
    }

    @Override
    public List<WebsiteNavigationRevisionRespVO> getHistory(Long siteId, String locale) {
        return revisionMapper.selectHistory(siteId, SeoLocaleUtils.normalize(locale)).stream()
                .map(revision -> {
                    WebsiteNavigationRevisionRespVO response = new WebsiteNavigationRevisionRespVO();
                    response.setRevisionId(revision.getId());
                    response.setRevisionNo(revision.getRevisionNo());
                    response.setVersion(revision.getVersion());
                    response.setStatus(revision.getStatus());
                    response.setPublishedTime(revision.getPublishedTime());
                    response.setPublishedBy(revision.getPublishedBy());
                    response.setUpdateTime(revision.getUpdateTime());
                    return response;
                }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreDraft(WebsiteNavigationRestoreReqVO reqVO) {
        WebsiteNavigationRevisionDO draft = getRequiredDraft(reqVO.getDraftRevisionId());
        WebsiteNavigationRevisionDO source = revisionMapper.selectByIdForTenant(reqVO.getSourceRevisionId());
        if (source == null
                || (!WebsiteNavigationRevisionStatusEnum.PUBLISHED.getCode().equals(source.getStatus())
                        && !WebsiteNavigationRevisionStatusEnum.ARCHIVED.getCode().equals(source.getStatus()))
                || !Objects.equals(source.getSiteId(), draft.getSiteId())
                || !Objects.equals(source.getLocale(), draft.getLocale())) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        List<WebsiteNavigationItemDO> sourceItems = itemMapper.selectListByRevisionId(source.getId());
        validatePublishableItems(sourceItems, draft.getSiteId());
        int affected = revisionMapper.bumpDraftVersionAtomic(draft.getId(), reqVO.getDraftVersion(),
                currentTenantId(), currentUpdater());
        if (affected == 0) {
            classifyAtomicFailure(draft.getId());
        }
        itemMapper.deleteByRevisionId(draft.getId());
        insertItems(sourceItems.stream().map(item -> new WebsiteNavigationItemDO()
                .setRevisionId(draft.getId())
                .setItemKey(item.getItemKey())
                .setParentItemKey(item.getParentItemKey())
                .setItemType(item.getItemType())
                .setLabel(item.getLabel())
                .setPageKey(item.getPageKey())
                .setTargetKey(item.getTargetKey())
                .setCategoryId(item.getCategoryId())
                .setSort(item.getSort())
                .setVisible(item.getVisible())
                .setOpenMode(item.getOpenMode())
                .setStyleVariant(item.getStyleVariant())).toList());
    }

    @Override
    public AppWebsiteNavigationRespVO getPublished(Long siteId, String locale) {
        WebsiteNavigationRevisionDO published = revisionMapper.selectActive(siteId,
                SeoLocaleUtils.normalize(locale), WebsiteNavigationRevisionStatusEnum.PUBLISHED.getCode());
        if (published == null) {
            return null;
        }
        WebsiteNavigationTemplateEnum template = navigationTemplate(siteId);
        List<WebsiteNavigationItemDO> items = itemMapper.selectListByRevisionId(published.getId());
        return isTemplateCompatible(items, template) ? buildPublicResponse(published, template, items) : null;
    }

    @Override
    public WebsiteNavigationPreviewTicketRespVO createPreviewTicket(
            WebsiteNavigationPreviewTicketReqVO reqVO) {
        WebsiteNavigationRevisionDO draft = getRequiredDraft(reqVO.getRevisionId());
        if (!Objects.equals(draft.getVersion(), reqVO.getVersion())) {
            throw exception(NAVIGATION_VERSION_CONFLICT);
        }
        SeoSiteConfigDO siteConfig = siteConfigService.getRequiredSiteConfig(draft.getSiteId());
        String previewOrigin = originOf(siteConfig.getSiteUrl());
        WebsiteNavigationPreviewGrant grant = new WebsiteNavigationPreviewGrant(
                currentTenantId(), draft.getSiteId(), draft.getLocale(), draft.getId(), draft.getVersion(),
                previewOrigin);
        String ticket = randomToken("pv_");
        previewRedisDAO.setTicket(ticket, grant, PREVIEW_TICKET_TTL);
        String previewUrl = siteConfig.getSiteUrl() + "/preview/navigation#ticket=" + ticket
                + "&tenantId=" + currentTenantId();
        return new WebsiteNavigationPreviewTicketRespVO(previewUrl, (int) PREVIEW_TICKET_TTL.toSeconds());
    }

    @Override
    public AppWebsiteNavigationPreviewSessionRespVO exchangePreviewTicket(String ticket, String requestOrigin) {
        WebsiteNavigationPreviewGrant grant = previewRedisDAO.consumeTicket(ticket);
        verifyPreviewGrant(grant, requestOrigin);
        String session = randomToken("ps_");
        previewRedisDAO.setSession(session, grant, PREVIEW_SESSION_TTL);
        return new AppWebsiteNavigationPreviewSessionRespVO(session,
                (int) PREVIEW_SESSION_TTL.toSeconds());
    }

    @Override
    public AppWebsiteNavigationRespVO getPreview(String session, String requestOrigin) {
        WebsiteNavigationPreviewGrant grant = previewRedisDAO.getSession(session);
        WebsiteNavigationRevisionDO revision = verifyPreviewGrant(grant, requestOrigin);
        WebsiteNavigationTemplateEnum template = navigationTemplate(revision.getSiteId());
        return buildPublicResponse(revision, template, itemMapper.selectListByRevisionId(revision.getId()));
    }

    private WebsiteNavigationRevisionDO createDraft(Long siteId, String locale,
                                                     WebsiteNavigationTemplateEnum template) {
        WebsiteNavigationRevisionDO published = revisionMapper.selectActive(siteId, locale,
                WebsiteNavigationRevisionStatusEnum.PUBLISHED.getCode());
        Integer maxRevisionNo = revisionMapper.selectMaxRevisionNo(currentTenantId(), siteId, locale);
        WebsiteNavigationRevisionDO draft = new WebsiteNavigationRevisionDO()
                .setSiteId(siteId)
                .setLocale(locale)
                .setRevisionNo((maxRevisionNo == null ? 0 : maxRevisionNo) + 1)
                .setStatus(WebsiteNavigationRevisionStatusEnum.DRAFT.getCode())
                .setVersion(1);
        draft.setTenantId(currentTenantId());
        try {
            revisionMapper.insert(draft);
        } catch (DuplicateKeyException ex) {
            WebsiteNavigationRevisionDO concurrentDraft = revisionMapper.selectActive(siteId, locale,
                    WebsiteNavigationRevisionStatusEnum.DRAFT.getCode());
            if (concurrentDraft == null) {
                throw ex;
            }
            return concurrentDraft;
        }
        if (published == null) {
            insertItems(createSeedItems(draft.getId(), template));
        } else {
            List<WebsiteNavigationItemDO> publishedItems = itemMapper.selectListByRevisionId(published.getId());
            insertItems(isTemplateCompatible(publishedItems, template)
                    ? cloneItems(publishedItems, draft.getId())
                    : createSeedItems(draft.getId(), template));
        }
        return draft;
    }

    private List<WebsiteNavigationItemDO> createSeedItems(Long revisionId,
                                                          WebsiteNavigationTemplateEnum template) {
        if (template == WebsiteNavigationTemplateEnum.OAKVED_B2C) {
            return OAKVED_SEED_ITEMS.stream()
                    .map(seed -> oakvedItem(revisionId, seed))
                    .toList();
        }
        List<WebsiteNavigationItemDO> items = new ArrayList<>();
        for (WebsiteNavigationPageKeyEnum page : WebsiteNavigationPageKeyEnum.values()) {
            items.add(pageItem(revisionId, page, page.getDefaultLabel(), page.getDefaultSort(), true));
        }
        List<ProductCategoryNavigationRespDTO> categories = loadCategories();
        for (int index = 0; index < categories.size(); index++) {
            ProductCategoryNavigationRespDTO category = categories.get(index);
            items.add(categoryItem(revisionId, category.getId(), category.getName(),
                    (index + 1) * 10, true));
        }
        return items;
    }

    private List<WebsiteNavigationItemDO> cloneItems(List<WebsiteNavigationItemDO> sourceItems,
                                                     Long targetRevisionId) {
        return sourceItems.stream()
                .map(source -> new WebsiteNavigationItemDO()
                        .setRevisionId(targetRevisionId)
                        .setItemKey(source.getItemKey())
                        .setParentItemKey(source.getParentItemKey())
                        .setItemType(source.getItemType())
                        .setLabel(source.getLabel())
                        .setPageKey(source.getPageKey())
                        .setTargetKey(source.getTargetKey())
                        .setCategoryId(source.getCategoryId())
                        .setSort(source.getSort())
                        .setVisible(source.getVisible())
                        .setOpenMode(source.getOpenMode())
                        .setStyleVariant(source.getStyleVariant()))
                .toList();
    }

    private List<WebsiteNavigationItemDO> validateAndConvertItems(
            List<WebsiteNavigationItemSaveReqVO> requestItems, Long revisionId,
            WebsiteNavigationTemplateEnum template) {
        return validateAndConvertItems(requestItems, revisionId, template, loadCategoryMap());
    }

    private List<WebsiteNavigationItemDO> validateAndConvertItems(
            List<WebsiteNavigationItemSaveReqVO> requestItems, Long revisionId,
            WebsiteNavigationTemplateEnum template,
            Map<Long, ProductCategoryNavigationRespDTO> categoryMap) {
        if (requestItems == null || requestItems.isEmpty() || requestItems.size() > MAX_NAVIGATION_ITEMS) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        List<WebsiteNavigationItemDO> items = template == WebsiteNavigationTemplateEnum.OAKVED_B2C
                ? validateOakvedItems(requestItems, revisionId, categoryMap)
                : validateVanzItems(requestItems, revisionId, categoryMap);
        validateTree(items);
        return items;
    }

    private List<WebsiteNavigationItemDO> validateVanzItems(
            List<WebsiteNavigationItemSaveReqVO> requestItems, Long revisionId,
            Map<Long, ProductCategoryNavigationRespDTO> categoryMap) {
        Set<WebsiteNavigationPageKeyEnum> pageKeys = EnumSet.noneOf(WebsiteNavigationPageKeyEnum.class);
        Set<Long> categoryIds = new HashSet<>();
        List<WebsiteNavigationItemDO> items = new ArrayList<>();
        for (WebsiteNavigationItemSaveReqVO requestItem : requestItems) {
            validateSort(requestItem.getSort());
            validateVisible(requestItem.getVisible());
            if (WebsiteNavigationItemTypeEnum.PAGE.getCode().equals(requestItem.getItemType())) {
                WebsiteNavigationPageKeyEnum page = WebsiteNavigationPageKeyEnum.fromCode(requestItem.getPageKey());
                if (page == null || !pageKeys.add(page) || StrUtil.isBlank(requestItem.getLabel())) {
                    throw exception(NAVIGATION_CONFIG_INVALID);
                }
                items.add(pageItem(revisionId, page, requestItem.getLabel().trim(),
                        requestItem.getSort(), requestItem.getVisible()));
                continue;
            }
            if (!WebsiteNavigationItemTypeEnum.CATEGORY.getCode().equals(requestItem.getItemType())
                    || requestItem.getCategoryId() == null || !categoryIds.add(requestItem.getCategoryId())) {
                throw exception(NAVIGATION_CONFIG_INVALID);
            }
            ProductCategoryNavigationRespDTO category = categoryMap.get(requestItem.getCategoryId());
            if (category == null && Boolean.TRUE.equals(requestItem.getVisible())) {
                throw exception(NAVIGATION_CATEGORY_UNAVAILABLE, requestItem.getCategoryId());
            }
            if (Boolean.TRUE.equals(requestItem.getSyncCategoryName())
                    && StrUtil.isBlank(requestItem.getLabel())) {
                throw exception(NAVIGATION_CONFIG_INVALID);
            }
            String label = category == null ? defaultCategoryLabel(requestItem)
                    : Boolean.TRUE.equals(requestItem.getSyncCategoryName())
                            ? requestItem.getLabel().trim() : category.getName();
            items.add(categoryItem(revisionId, requestItem.getCategoryId(), label,
                    requestItem.getSort(), requestItem.getVisible()));
        }
        if (pageKeys.size() != WebsiteNavigationPageKeyEnum.values().length) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        return items;
    }

    private void syncCategoryNames(List<WebsiteNavigationItemSaveReqVO> requestItems,
                                   Map<Long, ProductCategoryNavigationRespDTO> categoryMap) {
        requestItems.stream()
                .filter(item -> WebsiteNavigationItemTypeEnum.CATEGORY.getCode().equals(item.getItemType()))
                .filter(item -> Boolean.TRUE.equals(item.getSyncCategoryName()))
                .filter(item -> item.getCategoryId() != null && StrUtil.isNotBlank(item.getLabel()))
                .forEach(item -> {
                    ProductCategoryNavigationRespDTO category = categoryMap.get(item.getCategoryId());
                    String normalizedName = item.getLabel().trim();
                    if (category == null || Objects.equals(category.getName(), normalizedName)) {
                        return;
                    }
                    ProductCategoryNavigationNameUpdateReqDTO request =
                            new ProductCategoryNavigationNameUpdateReqDTO();
                    request.setId(item.getCategoryId());
                    request.setName(normalizedName);
                    productCategoryApi.updateNavigationCategoryName(request).getCheckedData();
                });
    }

    private List<WebsiteNavigationItemDO> validateOakvedItems(
            List<WebsiteNavigationItemSaveReqVO> requestItems, Long revisionId,
            Map<Long, ProductCategoryNavigationRespDTO> categoryMap) {
        Map<String, OakvedSeedSpec> primarySpecs = OAKVED_SEED_ITEMS.stream()
                .filter(seed -> StrUtil.isBlank(seed.parentItemKey()))
                .collect(Collectors.toMap(OakvedSeedSpec::itemKey, Function.identity()));
        Set<String> itemKeys = new HashSet<>();
        List<WebsiteNavigationItemDO> items = new ArrayList<>();
        for (WebsiteNavigationItemSaveReqVO requestItem : requestItems) {
            validateSort(requestItem.getSort());
            validateVisible(requestItem.getVisible());
            String itemKey = normalizeItemKey(requestItem.getItemKey());
            if (!itemKeys.add(itemKey) || StrUtil.isBlank(requestItem.getLabel())) {
                throw exception(NAVIGATION_CONFIG_INVALID);
            }
            String parentItemKey = StrUtil.blankToDefault(requestItem.getParentItemKey(), "").trim();
            String itemType = StrUtil.blankToDefault(requestItem.getItemType(), "")
                    .trim().toUpperCase(Locale.ROOT);
            String targetKey = StrUtil.isBlank(requestItem.getTargetKey())
                    ? null : requestItem.getTargetKey().trim();
            Long categoryId = requestItem.getCategoryId();
            String label = requestItem.getLabel().trim();
            String openMode = normalizeOpenMode(requestItem.getOpenMode());
            String styleVariant = normalizeStyleVariant(requestItem.getStyleVariant());

            if (WebsiteNavigationItemTypeEnum.DIRECTORY.getCode().equals(itemType)) {
                if (targetKey != null || categoryId != null) {
                    throw exception(NAVIGATION_CONFIG_INVALID);
                }
            } else if (WebsiteNavigationItemTypeEnum.ROUTE.getCode().equals(itemType)
                    || WebsiteNavigationItemTypeEnum.FILTER.getCode().equals(itemType)) {
                WebsiteNavigationTargetEnum target = WebsiteNavigationTargetEnum.fromCode(targetKey);
                if (target == null || !itemType.equals(target.getItemType()) || categoryId != null) {
                    throw exception(NAVIGATION_CONFIG_INVALID);
                }
            } else if (WebsiteNavigationItemTypeEnum.CATEGORY.getCode().equals(itemType)) {
                if (categoryId == null || targetKey != null) {
                    throw exception(NAVIGATION_CONFIG_INVALID);
                }
                ProductCategoryNavigationRespDTO category = categoryMap.get(categoryId);
                if (category == null && Boolean.TRUE.equals(requestItem.getVisible())) {
                    throw exception(NAVIGATION_CATEGORY_UNAVAILABLE, categoryId);
                }
                label = category == null ? defaultCategoryLabel(requestItem) : category.getName();
            } else {
                throw exception(NAVIGATION_CONFIG_INVALID);
            }

            OakvedSeedSpec primarySpec = primarySpecs.get(itemKey);
            if (StrUtil.isBlank(parentItemKey)) {
                if (primarySpec == null
                        || !primarySpec.itemType().equals(itemType)
                        || !Objects.equals(primarySpec.targetKey(), targetKey)
                        || !primarySpec.styleVariant().equals(styleVariant)) {
                    throw exception(NAVIGATION_CONFIG_INVALID);
                }
            }
            items.add(new WebsiteNavigationItemDO()
                    .setRevisionId(revisionId)
                    .setItemKey(itemKey)
                    .setParentItemKey(parentItemKey)
                    .setItemType(itemType)
                    .setLabel(label)
                    .setPageKey(null)
                    .setTargetKey(targetKey)
                    .setCategoryId(categoryId)
                    .setSort(requestItem.getSort())
                    .setVisible(requestItem.getVisible())
                    .setOpenMode(openMode)
                    .setStyleVariant(styleVariant));
        }
        Set<String> rootKeys = items.stream()
                .filter(item -> StrUtil.isBlank(item.getParentItemKey()))
                .map(WebsiteNavigationItemDO::getItemKey)
                .collect(Collectors.toSet());
        if (!rootKeys.equals(OAKVED_PRIMARY_KEYS)) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        return items;
    }

    private static void validateTree(List<WebsiteNavigationItemDO> items) {
        Map<String, WebsiteNavigationItemDO> itemMap = items.stream()
                .collect(Collectors.toMap(WebsiteNavigationItemDO::getItemKey, Function.identity(),
                        (left, right) -> {
                            throw exception(NAVIGATION_CONFIG_INVALID);
                        }, LinkedHashMap::new));
        Map<String, Integer> depthCache = new HashMap<>();
        for (WebsiteNavigationItemDO item : items) {
            int depth = resolveDepth(item.getItemKey(), itemMap, depthCache, new HashSet<>());
            if (depth > MAX_NAVIGATION_DEPTH) {
                throw exception(NAVIGATION_CONFIG_INVALID);
            }
        }
    }

    private static int resolveDepth(String itemKey, Map<String, WebsiteNavigationItemDO> itemMap,
                                    Map<String, Integer> depthCache, Set<String> visiting) {
        Integer cached = depthCache.get(itemKey);
        if (cached != null) {
            return cached;
        }
        WebsiteNavigationItemDO item = itemMap.get(itemKey);
        if (item == null || !visiting.add(itemKey)) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        String parentItemKey = item.getParentItemKey();
        int depth = StrUtil.isBlank(parentItemKey)
                ? 1 : resolveDepth(parentItemKey, itemMap, depthCache, visiting) + 1;
        visiting.remove(itemKey);
        depthCache.put(itemKey, depth);
        return depth;
    }

    private void validatePublishableItems(List<WebsiteNavigationItemDO> items, Long siteId) {
        List<WebsiteNavigationItemSaveReqVO> requestItems = items.stream().map(item -> {
            WebsiteNavigationItemSaveReqVO request = new WebsiteNavigationItemSaveReqVO();
            request.setItemKey(item.getItemKey());
            request.setParentItemKey(item.getParentItemKey());
            request.setItemType(item.getItemType());
            request.setPageKey(item.getPageKey());
            request.setTargetKey(item.getTargetKey());
            request.setCategoryId(item.getCategoryId());
            request.setLabel(item.getLabel());
            request.setSort(item.getSort());
            request.setVisible(item.getVisible());
            request.setOpenMode(item.getOpenMode());
            request.setStyleVariant(item.getStyleVariant());
            return request;
        }).toList();
        validateAndConvertItems(requestItems, items.isEmpty() ? 0L : items.get(0).getRevisionId(),
                navigationTemplate(siteId));
    }

    private WebsiteNavigationDraftRespVO buildDraftResponse(WebsiteNavigationRevisionDO draft,
                                                             WebsiteNavigationTemplateEnum template) {
        List<WebsiteNavigationItemDO> items = itemMapper.selectListByRevisionId(draft.getId());
        Map<Long, ProductCategoryNavigationRespDTO> categoryMap = loadCategoryMap();
        Set<Long> selectedCategoryIds = items.stream()
                .map(WebsiteNavigationItemDO::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        WebsiteNavigationRevisionDO published = revisionMapper.selectActive(draft.getSiteId(), draft.getLocale(),
                WebsiteNavigationRevisionStatusEnum.PUBLISHED.getCode());
        WebsiteNavigationDraftRespVO response = new WebsiteNavigationDraftRespVO();
        response.setRevisionId(draft.getId());
        response.setSiteId(draft.getSiteId());
        response.setLocale(draft.getLocale());
        response.setNavigationTemplate(template.getCode());
        response.setRevisionNo(draft.getRevisionNo());
        response.setVersion(draft.getVersion());
        response.setStatus(draft.getStatus());
        response.setPublishedVersion(published == null ? null : published.getVersion());
        response.setPublishedRevisionNo(published == null ? null : published.getRevisionNo());
        response.setLastPublishedTime(published == null ? null : published.getPublishedTime());
        response.setLastPublishedBy(published == null ? null : published.getPublishedBy());
        response.setItems(items.stream().map(item -> toAdminItem(item, categoryMap)).toList());
        response.setPublishedItems(published == null ? List.of()
                : itemMapper.selectListByRevisionId(published.getId()).stream()
                        .map(item -> toAdminItem(item, categoryMap)).toList());
        response.setCategoryOptions(categoryMap.values().stream().map(category -> {
            WebsiteNavigationCategoryOptionRespVO option = new WebsiteNavigationCategoryOptionRespVO();
            option.setId(category.getId());
            option.setName(category.getName());
            option.setSort(category.getSort());
            option.setPublishedProductCount(category.getPublishedProductCount());
            option.setSelected(selectedCategoryIds.contains(category.getId()));
            return option;
        }).toList());
        response.setTargetOptions(template == WebsiteNavigationTemplateEnum.OAKVED_B2C
                ? Arrays.stream(WebsiteNavigationTargetEnum.values())
                        .map(target -> new WebsiteNavigationTargetOptionRespVO(
                                target.getCode(), target.getItemType(), target.getLabel(), target.getHref()))
                        .toList()
                : List.of());
        return response;
    }

    private WebsiteNavigationItemRespVO toAdminItem(WebsiteNavigationItemDO item,
                                                     Map<Long, ProductCategoryNavigationRespDTO> categoryMap) {
        WebsiteNavigationItemRespVO response = new WebsiteNavigationItemRespVO();
        response.setItemKey(item.getItemKey());
        response.setParentItemKey(item.getParentItemKey());
        response.setItemType(item.getItemType());
        response.setPageKey(item.getPageKey());
        response.setTargetKey(item.getTargetKey());
        response.setCategoryId(item.getCategoryId());
        response.setSort(item.getSort());
        response.setVisible(item.getVisible());
        response.setOpenMode(StrUtil.blankToDefault(item.getOpenMode(), SELF_OPEN_MODE));
        response.setStyleVariant(StrUtil.blankToDefault(item.getStyleVariant(), DEFAULT_STYLE_VARIANT));
        if (item.getCategoryId() == null) {
            response.setLabel(item.getLabel());
            response.setAvailable(true);
            response.setPublishedProductCount(null);
            return response;
        }
        ProductCategoryNavigationRespDTO category = categoryMap.get(item.getCategoryId());
        response.setLabel(category == null ? item.getLabel() : category.getName());
        response.setAvailable(category != null);
        response.setPublishedProductCount(category == null ? 0L : category.getPublishedProductCount());
        return response;
    }

    private AppWebsiteNavigationRespVO buildPublicResponse(
            WebsiteNavigationRevisionDO revision, WebsiteNavigationTemplateEnum template,
            List<WebsiteNavigationItemDO> rows) {
        Map<Long, ProductCategoryNavigationRespDTO> categoryMap = loadCategoryMap();
        Comparator<WebsiteNavigationItemDO> comparator = Comparator
                .comparing((WebsiteNavigationItemDO item) -> Objects.requireNonNullElse(item.getSort(), 0))
                .thenComparing(item -> Objects.requireNonNullElse(item.getItemKey(), ""));
        Map<String, List<WebsiteNavigationItemDO>> childrenByParent = rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getVisible()))
                .collect(Collectors.groupingBy(
                        row -> StrUtil.blankToDefault(row.getParentItemKey(), ""),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(comparator)
                                .toList())));
        List<AppWebsiteNavigationItemRespVO> items = buildPublicChildren(
                "", childrenByParent, categoryMap, 1);
        AppWebsiteNavigationRespVO response = new AppWebsiteNavigationRespVO();
        response.setSiteId(revision.getSiteId());
        response.setLocale(revision.getLocale());
        response.setNavigationTemplate(template.getCode());
        response.setRevisionId(revision.getId());
        response.setVersion(revision.getVersion());
        response.setItems(items);
        return response;
    }

    private List<AppWebsiteNavigationItemRespVO> buildPublicChildren(
            String parentItemKey, Map<String, List<WebsiteNavigationItemDO>> childrenByParent,
            Map<Long, ProductCategoryNavigationRespDTO> categoryMap, int depth) {
        if (depth > MAX_NAVIGATION_DEPTH) {
            return List.of();
        }
        return childrenByParent.getOrDefault(parentItemKey, List.of()).stream()
                .map(row -> toPublicItem(row, childrenByParent, categoryMap, depth))
                .filter(Objects::nonNull)
                .toList();
    }

    private AppWebsiteNavigationItemRespVO toPublicItem(
            WebsiteNavigationItemDO row, Map<String, List<WebsiteNavigationItemDO>> childrenByParent,
            Map<Long, ProductCategoryNavigationRespDTO> categoryMap, int depth) {
        ProductCategoryNavigationRespDTO category = row.getCategoryId() == null
                ? null : categoryMap.get(row.getCategoryId());
        if (WebsiteNavigationItemTypeEnum.CATEGORY.getCode().equals(row.getItemType()) && category == null) {
            return null;
        }
        String href = resolveHref(row, category);
        if (!WebsiteNavigationItemTypeEnum.DIRECTORY.getCode().equals(row.getItemType())
                && StrUtil.isBlank(href)) {
            return null;
        }
        AppWebsiteNavigationItemRespVO item = new AppWebsiteNavigationItemRespVO();
        item.setKey(row.getItemKey());
        item.setLabel(category == null ? row.getLabel() : category.getName());
        item.setHref(href);
        item.setItemType(row.getItemType());
        item.setOpenMode(StrUtil.blankToDefault(row.getOpenMode(), SELF_OPEN_MODE));
        item.setStyleVariant(StrUtil.blankToDefault(row.getStyleVariant(), DEFAULT_STYLE_VARIANT));
        item.setCategoryId(category == null ? null : category.getId());
        item.setPublishedProductCount(category == null ? null : category.getPublishedProductCount());
        item.setChildren(buildPublicChildren(row.getItemKey(), childrenByParent, categoryMap, depth + 1));
        return item;
    }

    private static String resolveHref(WebsiteNavigationItemDO row,
                                      ProductCategoryNavigationRespDTO category) {
        if (WebsiteNavigationItemTypeEnum.PAGE.getCode().equals(row.getItemType())) {
            WebsiteNavigationPageKeyEnum page = WebsiteNavigationPageKeyEnum.fromCode(row.getPageKey());
            return page == null ? null : page.getHref();
        }
        if (WebsiteNavigationItemTypeEnum.CATEGORY.getCode().equals(row.getItemType())) {
            return category == null ? null : "/products/category/" + category.getId();
        }
        if (WebsiteNavigationItemTypeEnum.DIRECTORY.getCode().equals(row.getItemType())) {
            return "";
        }
        WebsiteNavigationTargetEnum target = WebsiteNavigationTargetEnum.fromCode(row.getTargetKey());
        return target == null || !Objects.equals(target.getItemType(), row.getItemType())
                ? null : target.getHref();
    }

    private WebsiteNavigationRevisionDO verifyPreviewGrant(
            WebsiteNavigationPreviewGrant grant, String requestOrigin) {
        if (grant == null || !Objects.equals(grant.getTenantId(), currentTenantId())) {
            throw exception(NAVIGATION_PREVIEW_EXPIRED);
        }
        validateOrigin(grant.getPreviewOrigin(), requestOrigin);
        WebsiteNavigationRevisionDO revision = revisionMapper.selectByIdForTenant(grant.getRevisionId());
        if (revision == null
                || !WebsiteNavigationRevisionStatusEnum.DRAFT.getCode().equals(revision.getStatus())
                || !Objects.equals(revision.getSiteId(), grant.getSiteId())
                || !Objects.equals(revision.getLocale(), grant.getLocale())
                || !Objects.equals(revision.getVersion(), grant.getVersion())) {
            throw exception(NAVIGATION_PREVIEW_EXPIRED);
        }
        return revision;
    }

    private WebsiteNavigationRevisionDO getRequiredDraft(Long revisionId) {
        WebsiteNavigationRevisionDO revision = revisionMapper.selectByIdForTenant(revisionId);
        if (revision == null) {
            throw exception(NAVIGATION_REVISION_NOT_EXISTS);
        }
        if (!WebsiteNavigationRevisionStatusEnum.DRAFT.getCode().equals(revision.getStatus())) {
            throw exception(NAVIGATION_VERSION_CONFLICT);
        }
        return revision;
    }

    private void classifyAtomicFailure(Long revisionId) {
        getRequiredDraft(revisionId);
        throw exception(NAVIGATION_VERSION_CONFLICT);
    }

    private void ensureDraftTemplate(WebsiteNavigationRevisionDO draft,
                                     WebsiteNavigationTemplateEnum template) {
        List<WebsiteNavigationItemDO> currentItems = itemMapper.selectListByRevisionId(draft.getId());
        if (isTemplateCompatible(currentItems, template)) {
            return;
        }
        int affected = revisionMapper.bumpDraftVersionAtomic(draft.getId(), draft.getVersion(),
                currentTenantId(), currentUpdater());
        if (affected == 0) {
            classifyAtomicFailure(draft.getId());
        }
        itemMapper.deleteByRevisionId(draft.getId());
        insertItems(createSeedItems(draft.getId(), template));
        draft.setVersion(draft.getVersion() + 1);
    }

    private WebsiteNavigationTemplateEnum navigationTemplate(Long siteId) {
        SeoSiteConfigDO config = siteConfigService.getSiteConfig(siteId);
        String code = config == null || StrUtil.isBlank(config.getNavigationTemplate())
                ? WebsiteNavigationTemplateEnum.VANZ_B2B.getCode()
                : config.getNavigationTemplate();
        WebsiteNavigationTemplateEnum template = WebsiteNavigationTemplateEnum.fromCode(code);
        if (template == null) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        return template;
    }

    private static boolean isTemplateCompatible(List<WebsiteNavigationItemDO> items,
                                                WebsiteNavigationTemplateEnum template) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        if (template == WebsiteNavigationTemplateEnum.OAKVED_B2C) {
            Set<String> rootKeys = items.stream()
                    .filter(item -> StrUtil.isBlank(item.getParentItemKey()))
                    .map(WebsiteNavigationItemDO::getItemKey)
                    .collect(Collectors.toSet());
            return rootKeys.equals(OAKVED_PRIMARY_KEYS);
        }
        return items.stream().anyMatch(item ->
                WebsiteNavigationItemTypeEnum.PAGE.getCode().equals(item.getItemType()));
    }

    private List<ProductCategoryNavigationRespDTO> loadCategories() {
        List<ProductCategoryNavigationRespDTO> categories = productCategoryApi
                .getNavigationCategoryList().getCheckedData();
        return categories == null ? List.of() : categories;
    }

    private Map<Long, ProductCategoryNavigationRespDTO> loadCategoryMap() {
        return loadCategories().stream().collect(Collectors.toMap(
                ProductCategoryNavigationRespDTO::getId, Function.identity(), (left, right) -> left,
                LinkedHashMap::new));
    }

    private void insertItems(List<WebsiteNavigationItemDO> items) {
        for (WebsiteNavigationItemDO item : items) {
            item.setTenantId(currentTenantId());
            itemMapper.insert(item);
        }
    }

    private static WebsiteNavigationItemDO pageItem(Long revisionId, WebsiteNavigationPageKeyEnum page,
                                                     String label, Integer sort, Boolean visible) {
        return new WebsiteNavigationItemDO()
                .setRevisionId(revisionId)
                .setItemKey(page.itemKey())
                .setParentItemKey("")
                .setItemType(WebsiteNavigationItemTypeEnum.PAGE.getCode())
                .setLabel(label)
                .setPageKey(page.getCode())
                .setTargetKey(null)
                .setCategoryId(null)
                .setSort(sort)
                .setVisible(visible)
                .setOpenMode(SELF_OPEN_MODE)
                .setStyleVariant(DEFAULT_STYLE_VARIANT);
    }

    private static WebsiteNavigationItemDO categoryItem(Long revisionId, Long categoryId, String label,
                                                         Integer sort, Boolean visible) {
        return new WebsiteNavigationItemDO()
                .setRevisionId(revisionId)
                .setItemKey("CATEGORY_" + categoryId)
                .setParentItemKey(WebsiteNavigationPageKeyEnum.PRODUCTS.itemKey())
                .setItemType(WebsiteNavigationItemTypeEnum.CATEGORY.getCode())
                .setLabel(label)
                .setPageKey(null)
                .setTargetKey(null)
                .setCategoryId(categoryId)
                .setSort(sort)
                .setVisible(visible)
                .setOpenMode(SELF_OPEN_MODE)
                .setStyleVariant(DEFAULT_STYLE_VARIANT);
    }

    private static WebsiteNavigationItemDO oakvedItem(Long revisionId, OakvedSeedSpec seed) {
        return new WebsiteNavigationItemDO()
                .setRevisionId(revisionId)
                .setItemKey(seed.itemKey())
                .setParentItemKey(seed.parentItemKey())
                .setItemType(seed.itemType())
                .setLabel(seed.label())
                .setPageKey(null)
                .setTargetKey(seed.targetKey())
                .setCategoryId(null)
                .setSort(seed.sort())
                .setVisible(true)
                .setOpenMode(SELF_OPEN_MODE)
                .setStyleVariant(seed.styleVariant());
    }

    private static OakvedSeedSpec oakvedSeed(String itemKey, String parentItemKey, String itemType,
                                              String targetKey, String label, Integer sort,
                                              String styleVariant) {
        return new OakvedSeedSpec(itemKey, parentItemKey, itemType, targetKey, label, sort, styleVariant);
    }

    private static String defaultCategoryLabel(WebsiteNavigationItemSaveReqVO item) {
        return StrUtil.isBlank(item.getLabel()) ? "Category #" + item.getCategoryId() : item.getLabel().trim();
    }

    private static void validateSort(Integer sort) {
        if (sort == null || sort < 0 || sort > 10_000) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
    }

    private static void validateVisible(Boolean visible) {
        if (visible == null) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
    }

    private static String normalizeItemKey(String itemKey) {
        String normalized = StrUtil.blankToDefault(itemKey, "").trim();
        if (!ITEM_KEY_PATTERN.matcher(normalized).matches()) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        return normalized;
    }

    private static String normalizeOpenMode(String openMode) {
        String normalized = StrUtil.blankToDefault(openMode, SELF_OPEN_MODE).trim();
        if (!SELF_OPEN_MODE.equals(normalized) && !BLANK_OPEN_MODE.equals(normalized)) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        return normalized;
    }

    private static String normalizeStyleVariant(String styleVariant) {
        String normalized = StrUtil.blankToDefault(styleVariant, DEFAULT_STYLE_VARIANT)
                .trim().toUpperCase(Locale.ROOT);
        if (!DEFAULT_STYLE_VARIANT.equals(normalized) && !SALE_STYLE_VARIANT.equals(normalized)) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        return normalized;
    }

    private static String randomToken(String prefix) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String originOf(String url) {
        try {
            URI uri = new URI(url);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null).toString();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw exception(NAVIGATION_PREVIEW_ORIGIN_MISMATCH);
        }
    }

    private static void validateOrigin(String expectedOrigin, String requestOrigin) {
        if (StrUtil.isBlank(requestOrigin)) {
            return;
        }
        if (!Objects.equals(expectedOrigin, originOf(requestOrigin))) {
            throw exception(NAVIGATION_PREVIEW_ORIGIN_MISMATCH);
        }
    }

    private static Long currentTenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private static String currentUpdater() {
        return Objects.toString(SecurityFrameworkUtils.getLoginUserId(), "");
    }

    private record OakvedSeedSpec(String itemKey, String parentItemKey, String itemType,
                                  String targetKey, String label, Integer sort,
                                  String styleVariant) {
    }

}
