package cn.iocoder.yudao.module.seo.service.navigation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.api.category.ProductCategoryApi;
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
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
    private static final String SELF_OPEN_MODE = "_self";

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
        WebsiteNavigationRevisionDO draft = revisionMapper.selectActive(siteId, normalizedLocale,
                WebsiteNavigationRevisionStatusEnum.DRAFT.getCode());
        if (draft == null) {
            draft = createDraft(siteId, normalizedLocale);
        }
        return buildDraftResponse(draft);
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
        List<WebsiteNavigationItemDO> items = validateAndConvertItems(reqVO.getItems(), draft.getId());
        int affected = revisionMapper.bumpDraftVersionAtomic(draft.getId(), reqVO.getVersion(),
                currentTenantId(), currentUpdater());
        if (affected == 0) {
            classifyAtomicFailure(draft.getId());
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
        validatePublishableItems(itemMapper.selectListByRevisionId(draft.getId()));
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
        validatePublishableItems(sourceItems);
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
                .setCategoryId(item.getCategoryId())
                .setSort(item.getSort())
                .setVisible(item.getVisible())
                .setOpenMode(item.getOpenMode())).toList());
    }

    @Override
    public AppWebsiteNavigationRespVO getPublished(Long siteId, String locale) {
        WebsiteNavigationRevisionDO published = revisionMapper.selectActive(siteId,
                SeoLocaleUtils.normalize(locale), WebsiteNavigationRevisionStatusEnum.PUBLISHED.getCode());
        return published == null ? null : buildPublicResponse(published);
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
        return buildPublicResponse(revision);
    }

    private WebsiteNavigationRevisionDO createDraft(Long siteId, String locale) {
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
            insertItems(createSeedItems(draft.getId()));
        } else {
            insertItems(cloneItems(published.getId(), draft.getId()));
        }
        return draft;
    }

    private List<WebsiteNavigationItemDO> createSeedItems(Long revisionId) {
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

    private List<WebsiteNavigationItemDO> cloneItems(Long sourceRevisionId, Long targetRevisionId) {
        return itemMapper.selectListByRevisionId(sourceRevisionId).stream()
                .map(source -> new WebsiteNavigationItemDO()
                        .setRevisionId(targetRevisionId)
                        .setItemKey(source.getItemKey())
                        .setParentItemKey(source.getParentItemKey())
                        .setItemType(source.getItemType())
                        .setLabel(source.getLabel())
                        .setPageKey(source.getPageKey())
                        .setCategoryId(source.getCategoryId())
                        .setSort(source.getSort())
                        .setVisible(source.getVisible())
                        .setOpenMode(source.getOpenMode()))
                .toList();
    }

    private List<WebsiteNavigationItemDO> validateAndConvertItems(
            List<WebsiteNavigationItemSaveReqVO> requestItems, Long revisionId) {
        if (requestItems == null || requestItems.isEmpty() || requestItems.size() > MAX_NAVIGATION_ITEMS) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        Map<Long, ProductCategoryNavigationRespDTO> categoryMap = loadCategoryMap();
        Set<WebsiteNavigationPageKeyEnum> pageKeys = EnumSet.noneOf(WebsiteNavigationPageKeyEnum.class);
        Set<Long> categoryIds = new HashSet<>();
        List<WebsiteNavigationItemDO> items = new ArrayList<>();
        for (WebsiteNavigationItemSaveReqVO requestItem : requestItems) {
            validateSort(requestItem.getSort());
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
            String label = category == null ? defaultCategoryLabel(requestItem) : category.getName();
            items.add(categoryItem(revisionId, requestItem.getCategoryId(), label,
                    requestItem.getSort(), requestItem.getVisible()));
        }
        if (pageKeys.size() != WebsiteNavigationPageKeyEnum.values().length) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
        return items;
    }

    private void validatePublishableItems(List<WebsiteNavigationItemDO> items) {
        List<WebsiteNavigationItemSaveReqVO> requestItems = items.stream().map(item -> {
            WebsiteNavigationItemSaveReqVO request = new WebsiteNavigationItemSaveReqVO();
            request.setItemType(item.getItemType());
            request.setPageKey(item.getPageKey());
            request.setCategoryId(item.getCategoryId());
            request.setLabel(item.getLabel());
            request.setSort(item.getSort());
            request.setVisible(item.getVisible());
            return request;
        }).toList();
        validateAndConvertItems(requestItems, items.isEmpty() ? 0L : items.get(0).getRevisionId());
    }

    private WebsiteNavigationDraftRespVO buildDraftResponse(WebsiteNavigationRevisionDO draft) {
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
        return response;
    }

    private WebsiteNavigationItemRespVO toAdminItem(WebsiteNavigationItemDO item,
                                                     Map<Long, ProductCategoryNavigationRespDTO> categoryMap) {
        WebsiteNavigationItemRespVO response = new WebsiteNavigationItemRespVO();
        response.setItemKey(item.getItemKey());
        response.setItemType(item.getItemType());
        response.setPageKey(item.getPageKey());
        response.setCategoryId(item.getCategoryId());
        response.setSort(item.getSort());
        response.setVisible(item.getVisible());
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

    private AppWebsiteNavigationRespVO buildPublicResponse(WebsiteNavigationRevisionDO revision) {
        List<WebsiteNavigationItemDO> rows = itemMapper.selectListByRevisionId(revision.getId());
        Map<Long, ProductCategoryNavigationRespDTO> categoryMap = loadCategoryMap();
        List<WebsiteNavigationItemDO> categoryRows = rows.stream()
                .filter(row -> WebsiteNavigationItemTypeEnum.CATEGORY.getCode().equals(row.getItemType()))
                .filter(row -> Boolean.TRUE.equals(row.getVisible()))
                .toList();
        List<AppWebsiteNavigationItemRespVO> items = new ArrayList<>();
        for (WebsiteNavigationItemDO row : rows) {
            if (!WebsiteNavigationItemTypeEnum.PAGE.getCode().equals(row.getItemType())
                    || !Boolean.TRUE.equals(row.getVisible())) {
                continue;
            }
            WebsiteNavigationPageKeyEnum page = WebsiteNavigationPageKeyEnum.fromCode(row.getPageKey());
            if (page == null) {
                continue;
            }
            AppWebsiteNavigationItemRespVO item = new AppWebsiteNavigationItemRespVO();
            item.setKey(row.getItemKey());
            item.setLabel(row.getLabel());
            item.setHref(page.getHref());
            item.setItemType(WebsiteNavigationItemTypeEnum.PAGE.getCode());
            item.setChildren(List.of());
            if (page == WebsiteNavigationPageKeyEnum.PRODUCTS) {
                item.setChildren(categoryRows.stream()
                        .map(categoryRow -> toPublicCategoryItem(categoryRow, categoryMap))
                        .filter(Objects::nonNull)
                        .toList());
            }
            items.add(item);
        }
        AppWebsiteNavigationRespVO response = new AppWebsiteNavigationRespVO();
        response.setSiteId(revision.getSiteId());
        response.setLocale(revision.getLocale());
        response.setRevisionId(revision.getId());
        response.setVersion(revision.getVersion());
        response.setItems(items);
        return response;
    }

    private AppWebsiteNavigationItemRespVO toPublicCategoryItem(
            WebsiteNavigationItemDO row, Map<Long, ProductCategoryNavigationRespDTO> categoryMap) {
        ProductCategoryNavigationRespDTO category = categoryMap.get(row.getCategoryId());
        if (category == null) {
            return null;
        }
        AppWebsiteNavigationItemRespVO item = new AppWebsiteNavigationItemRespVO();
        item.setKey(row.getItemKey());
        item.setLabel(category.getName());
        item.setHref("/products/category/" + category.getId());
        item.setItemType(WebsiteNavigationItemTypeEnum.CATEGORY.getCode());
        item.setCategoryId(category.getId());
        item.setPublishedProductCount(category.getPublishedProductCount());
        item.setChildren(List.of());
        return item;
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
                .setCategoryId(null)
                .setSort(sort)
                .setVisible(visible)
                .setOpenMode(SELF_OPEN_MODE);
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
                .setCategoryId(categoryId)
                .setSort(sort)
                .setVisible(visible)
                .setOpenMode(SELF_OPEN_MODE);
    }

    private static String defaultCategoryLabel(WebsiteNavigationItemSaveReqVO item) {
        return StrUtil.isBlank(item.getLabel()) ? "Category #" + item.getCategoryId() : item.getLabel().trim();
    }

    private static void validateSort(Integer sort) {
        if (sort == null || sort < 0 || sort > 10_000) {
            throw exception(NAVIGATION_CONFIG_INVALID);
        }
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

}
