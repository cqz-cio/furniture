package cn.iocoder.yudao.module.seo.service.navigation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.api.category.ProductCategoryApi;
import cn.iocoder.yudao.module.product.api.category.dto.ProductCategoryNavigationNameUpdateReqDTO;
import cn.iocoder.yudao.module.product.api.category.dto.ProductCategoryNavigationRespDTO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationDraftSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationItemSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPreviewTicketReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationRestoreReqVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationRespVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.navigation.WebsiteNavigationItemDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.navigation.WebsiteNavigationRevisionDO;
import cn.iocoder.yudao.module.seo.dal.mysql.navigation.WebsiteNavigationItemMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.navigation.WebsiteNavigationRevisionMapper;
import cn.iocoder.yudao.module.seo.dal.redis.navigation.WebsiteNavigationPreviewGrant;
import cn.iocoder.yudao.module.seo.dal.redis.navigation.WebsiteNavigationPreviewRedisDAO;
import cn.iocoder.yudao.module.seo.enums.navigation.WebsiteNavigationPageKeyEnum;
import cn.iocoder.yudao.module.seo.service.config.SeoSiteConfigService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsiteNavigationServiceImplTest {

    private static final Long TENANT_ID = 162L;

    private WebsiteNavigationServiceImpl service;
    private WebsiteNavigationRevisionMapper revisionMapper;
    private WebsiteNavigationItemMapper itemMapper;
    private ProductCategoryApi productCategoryApi;
    private SeoSiteConfigService siteConfigService;
    private WebsiteNavigationPreviewRedisDAO previewRedisDAO;

    @BeforeEach
    void setUp() {
        revisionMapper = mock(WebsiteNavigationRevisionMapper.class);
        itemMapper = mock(WebsiteNavigationItemMapper.class);
        productCategoryApi = mock(ProductCategoryApi.class);
        siteConfigService = mock(SeoSiteConfigService.class);
        previewRedisDAO = mock(WebsiteNavigationPreviewRedisDAO.class);
        service = new WebsiteNavigationServiceImpl();
        ReflectionTestUtils.setField(service, "revisionMapper", revisionMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "productCategoryApi", productCategoryApi);
        ReflectionTestUtils.setField(service, "siteConfigService", siteConfigService);
        ReflectionTestUtils.setField(service, "previewRedisDAO", previewRedisDAO);

        TenantContextHolder.setTenantId(TENANT_ID);
        LoginUser loginUser = new LoginUser().setId(100L).setTenantId(TENANT_ID).setUserType(1);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPublished_shouldResolveLiveCategoryNameAndStableRoute() {
        WebsiteNavigationRevisionDO revision = revision(8L, "PUBLISHED", 3);
        when(revisionMapper.selectActive(1L, "en", "PUBLISHED")).thenReturn(revision);
        when(itemMapper.selectListByRevisionId(8L)).thenReturn(List.of(
                pageItem(8L, "PAGE_HOME", "HOME", "Home", 10),
                pageItem(8L, "PAGE_PRODUCTS", "PRODUCTS", "Collections", 20),
                categoryItem(8L, 25L, "Old category name", 10)));
        when(productCategoryApi.getNavigationCategoryList()).thenReturn(CommonResult.success(List.of(
                category(25L, "Dining Chairs", 8L))));

        AppWebsiteNavigationRespVO response = service.getPublished(1L, " en ");

        assertThat(response.getRevisionId()).isEqualTo(8L);
        assertThat(response.getItems()).extracting("label").containsExactly("Home", "Collections");
        assertThat(response.getItems().get(1).getChildren()).hasSize(1);
        assertThat(response.getItems().get(1).getChildren().get(0).getLabel()).isEqualTo("Dining Chairs");
        assertThat(response.getItems().get(1).getChildren().get(0).getHref())
                .isEqualTo("/products/category/25");
        assertThat(response.getItems().get(1).getChildren().get(0).getPublishedProductCount()).isEqualTo(8L);
    }

    @Test
    void getPublished_shouldHideCategoryThatProductCenterNoLongerExposes() {
        WebsiteNavigationRevisionDO revision = revision(9L, "PUBLISHED", 4);
        when(revisionMapper.selectActive(1L, "en", "PUBLISHED")).thenReturn(revision);
        when(itemMapper.selectListByRevisionId(9L)).thenReturn(List.of(
                pageItem(9L, "PAGE_PRODUCTS", "PRODUCTS", "Products", 10),
                categoryItem(9L, 99L, "Removed category", 10)));
        when(productCategoryApi.getNavigationCategoryList()).thenReturn(CommonResult.success(List.of()));

        AppWebsiteNavigationRespVO response = service.getPublished(1L, "en");

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getChildren()).isEmpty();
    }

    @Test
    void saveDraft_shouldSynchronizeExplicitlyEditedCategoryName() {
        WebsiteNavigationRevisionDO draft = revision(30L, "DRAFT", 5);
        when(revisionMapper.selectByIdForTenant(30L)).thenReturn(draft);
        when(productCategoryApi.getNavigationCategoryList()).thenReturn(CommonResult.success(List.of(
                category(25L, "Dining Room", 3L))));
        when(revisionMapper.bumpDraftVersionAtomic(30L, 5, TENANT_ID, "100")).thenReturn(1);
        when(productCategoryApi.updateNavigationCategoryName(any()))
                .thenReturn(CommonResult.success(true));
        WebsiteNavigationDraftSaveReqVO request = draftSaveRequest(
                draft, categoryRequest(25L, "Contract Dining", true));

        service.saveDraft(request);

        ArgumentCaptor<ProductCategoryNavigationNameUpdateReqDTO> updateCaptor =
                ArgumentCaptor.forClass(ProductCategoryNavigationNameUpdateReqDTO.class);
        verify(productCategoryApi).updateNavigationCategoryName(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(25L);
        assertThat(updateCaptor.getValue().getName()).isEqualTo("Contract Dining");
        ArgumentCaptor<WebsiteNavigationItemDO> itemCaptor =
                ArgumentCaptor.forClass(WebsiteNavigationItemDO.class);
        verify(itemMapper, times(WebsiteNavigationPageKeyEnum.values().length + 1))
                .insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .filteredOn(item -> Long.valueOf(25L).equals(item.getCategoryId()))
                .extracting(WebsiteNavigationItemDO::getLabel)
                .containsExactly("Contract Dining");
    }

    @Test
    void saveDraft_shouldNotOverwriteCategoryNameWithoutExplicitEditFlag() {
        WebsiteNavigationRevisionDO draft = revision(31L, "DRAFT", 2);
        when(revisionMapper.selectByIdForTenant(31L)).thenReturn(draft);
        when(productCategoryApi.getNavigationCategoryList()).thenReturn(CommonResult.success(List.of(
                category(25L, "Current Category Name", 3L))));
        when(revisionMapper.bumpDraftVersionAtomic(31L, 2, TENANT_ID, "100")).thenReturn(1);
        WebsiteNavigationDraftSaveReqVO request = draftSaveRequest(
                draft, categoryRequest(25L, "Stale Browser Value", false));

        service.saveDraft(request);

        verify(productCategoryApi, times(0)).updateNavigationCategoryName(any());
        ArgumentCaptor<WebsiteNavigationItemDO> itemCaptor =
                ArgumentCaptor.forClass(WebsiteNavigationItemDO.class);
        verify(itemMapper, times(WebsiteNavigationPageKeyEnum.values().length + 1))
                .insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .filteredOn(item -> Long.valueOf(25L).equals(item.getCategoryId()))
                .extracting(WebsiteNavigationItemDO::getLabel)
                .containsExactly("Current Category Name");
    }

    @Test
    void saveDraft_shouldAllowAdditionalVanzPrimaryEntryUsingApprovedPage() {
        WebsiteNavigationRevisionDO draft = revision(32L, "DRAFT", 3);
        when(revisionMapper.selectByIdForTenant(32L)).thenReturn(draft);
        when(productCategoryApi.getNavigationCategoryList()).thenReturn(CommonResult.success(List.of()));
        when(revisionMapper.bumpDraftVersionAtomic(32L, 3, TENANT_ID, "100")).thenReturn(1);
        WebsiteNavigationItemSaveReqVO additionalPrimary = new WebsiteNavigationItemSaveReqVO();
        additionalPrimary.setItemKey("CUSTOM_CONTRACT_CATALOG");
        additionalPrimary.setParentItemKey("");
        additionalPrimary.setItemType("PAGE");
        additionalPrimary.setPageKey("PRODUCTS");
        additionalPrimary.setLabel("Contract Catalog");
        additionalPrimary.setSort(70);
        additionalPrimary.setVisible(true);
        WebsiteNavigationDraftSaveReqVO request = draftSaveRequest(draft, additionalPrimary);

        service.saveDraft(request);

        ArgumentCaptor<WebsiteNavigationItemDO> itemCaptor =
                ArgumentCaptor.forClass(WebsiteNavigationItemDO.class);
        verify(itemMapper, times(WebsiteNavigationPageKeyEnum.values().length + 1))
                .insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .filteredOn(item -> "CUSTOM_CONTRACT_CATALOG".equals(item.getItemKey()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getPageKey()).isEqualTo("PRODUCTS");
                    assertThat(item.getLabel()).isEqualTo("Contract Catalog");
                    assertThat(item.getParentItemKey()).isEmpty();
                });
    }

    @Test
    void getPublished_shouldReturnOakvedThreeLevelTreeAndSaleStyle() {
        WebsiteNavigationRevisionDO revision = revision(12L, "PUBLISHED", 4);
        when(revisionMapper.selectActive(1L, "en", "PUBLISHED")).thenReturn(revision);
        when(siteConfigService.getSiteConfig(1L)).thenReturn(new SeoSiteConfigDO()
                .setSiteId(1L)
                .setNavigationTemplate("OAKVED_B2C"));
        when(itemMapper.selectListByRevisionId(12L)).thenReturn(List.of(
                oakvedItem(12L, "OAKVED_NEW", "", "FILTER", "FILTER_NEW", "NEW", 10, "DEFAULT"),
                oakvedItem(12L, "OAKVED_COLLECTIONS", "", "FILTER", "FILTER_COLLECTIONS_ALL", "SHOP BY COLLECTIONS", 20, "DEFAULT"),
                oakvedItem(12L, "OAKVED_BEDROOM", "", "FILTER", "FILTER_ROOM_BEDROOM", "BEDROOM", 30, "DEFAULT"),
                oakvedItem(12L, "OAKVED_LIVING", "", "FILTER", "FILTER_ROOM_LIVING", "LIVING", 40, "DEFAULT"),
                oakvedItem(12L, "OAKVED_DINING", "", "FILTER", "FILTER_ROOM_DINING", "DINING", 50, "DEFAULT"),
                oakvedItem(12L, "OAKVED_BESPOKE", "", "FILTER", "FILTER_COLLECTION_BESPOKE", "BESPOKE", 60, "DEFAULT"),
                oakvedItem(12L, "OAKVED_DECOR", "", "FILTER", "FILTER_CATEGORY_DECOR", "DECOR", 70, "DEFAULT"),
                oakvedItem(12L, "OAKVED_SALE", "", "ROUTE", "ROUTE_SALE", "SALE", 80, "SALE"),
                oakvedItem(12L, "OAKVED_LIVING_SEATING", "OAKVED_LIVING", "DIRECTORY", null,
                        "Seating", 10, "DEFAULT"),
                oakvedItem(12L, "CUSTOM_SECTIONALS", "OAKVED_LIVING_SEATING", "FILTER",
                        "FILTER_CATEGORY_SOFA", "Sectionals", 10, "DEFAULT")));
        when(productCategoryApi.getNavigationCategoryList()).thenReturn(CommonResult.success(List.of()));

        AppWebsiteNavigationRespVO response = service.getPublished(1L, "en");

        assertThat(response.getNavigationTemplate()).isEqualTo("OAKVED_B2C");
        assertThat(response.getItems()).hasSize(8);
        assertThat(response.getItems().get(3).getChildren()).hasSize(1);
        assertThat(response.getItems().get(3).getChildren().get(0).getChildren()).hasSize(1);
        assertThat(response.getItems().get(3).getChildren().get(0).getChildren().get(0).getHref())
                .isEqualTo("/products?category=sofa");
        assertThat(response.getItems().get(7).getStyleVariant()).isEqualTo("SALE");
    }

    @Test
    void createPreviewTicket_shouldUseFragmentAndBindStoredGrant() {
        WebsiteNavigationRevisionDO draft = revision(10L, "DRAFT", 5);
        when(revisionMapper.selectByIdForTenant(10L)).thenReturn(draft);
        when(siteConfigService.getRequiredSiteConfig(1L)).thenReturn(new SeoSiteConfigDO()
                .setSiteId(1L)
                .setSiteUrl("https://www.vanz.example"));
        WebsiteNavigationPreviewTicketReqVO request = new WebsiteNavigationPreviewTicketReqVO();
        request.setRevisionId(10L);
        request.setVersion(5);

        var response = service.createPreviewTicket(request);

        assertThat(response.getPreviewUrl())
                .startsWith("https://www.vanz.example/preview/navigation#ticket=pv_")
                .endsWith("&tenantId=162")
                .doesNotContain("?token=");
        assertThat(response.getExpiresInSeconds()).isEqualTo(600);
        verify(previewRedisDAO).setTicket(anyString(), eq(new WebsiteNavigationPreviewGrant(
                TENANT_ID, 1L, "en", 10L, 5, "https://www.vanz.example")), eq(Duration.ofMinutes(10)));
    }

    @Test
    void exchangePreviewTicket_shouldConsumeTicketAndIssueReadOnlySession() {
        WebsiteNavigationPreviewGrant grant = new WebsiteNavigationPreviewGrant(
                TENANT_ID, 1L, "en", 11L, 6, "https://www.vanz.example");
        when(previewRedisDAO.consumeTicket("pv_once")).thenReturn(grant);
        when(revisionMapper.selectByIdForTenant(11L)).thenReturn(revision(11L, "DRAFT", 6));

        var response = service.exchangePreviewTicket("pv_once", "https://www.vanz.example");

        assertThat(response.getSession()).startsWith("ps_");
        assertThat(response.getExpiresInSeconds()).isEqualTo(1800);
        verify(previewRedisDAO).setSession(eq(response.getSession()), eq(grant), eq(Duration.ofMinutes(30)));
    }

    @Test
    void getHistory_shouldReturnPublishedAndArchivedRevisionDetails() {
        LocalDateTime publishedTime = LocalDateTime.of(2026, 8, 4, 12, 30);
        WebsiteNavigationRevisionDO published = revision(20L, "PUBLISHED", 8)
                .setRevisionNo(4)
                .setPublishedTime(publishedTime)
                .setPublishedBy("100");
        WebsiteNavigationRevisionDO archived = revision(19L, "ARCHIVED", 7)
                .setRevisionNo(3);
        when(revisionMapper.selectHistory(1L, "en")).thenReturn(List.of(published, archived));

        var result = service.getHistory(1L, " EN ");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRevisionId()).isEqualTo(20L);
        assertThat(result.get(0).getRevisionNo()).isEqualTo(4);
        assertThat(result.get(0).getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.get(0).getPublishedTime()).isEqualTo(publishedTime);
        assertThat(result.get(0).getPublishedBy()).isEqualTo("100");
    }

    @Test
    void restoreDraft_shouldCopyArchivedRevisionWithoutPublishingIt() {
        WebsiteNavigationRevisionDO draft = revision(22L, "DRAFT", 9);
        WebsiteNavigationRevisionDO archived = revision(21L, "ARCHIVED", 8);
        List<WebsiteNavigationItemDO> sourceItems = Arrays.stream(WebsiteNavigationPageKeyEnum.values())
                .map(page -> pageItem(archived.getId(), page.itemKey(), page.getCode(),
                        page.getDefaultLabel(), page.getDefaultSort()))
                .toList();
        when(revisionMapper.selectByIdForTenant(22L)).thenReturn(draft);
        when(revisionMapper.selectByIdForTenant(21L)).thenReturn(archived);
        when(itemMapper.selectListByRevisionId(21L)).thenReturn(sourceItems);
        when(productCategoryApi.getNavigationCategoryList()).thenReturn(CommonResult.success(List.of()));
        when(revisionMapper.bumpDraftVersionAtomic(22L, 9, TENANT_ID, "100")).thenReturn(1);
        WebsiteNavigationRestoreReqVO request = new WebsiteNavigationRestoreReqVO();
        request.setDraftRevisionId(22L);
        request.setDraftVersion(9);
        request.setSourceRevisionId(21L);

        service.restoreDraft(request);

        verify(itemMapper).deleteByRevisionId(22L);
        ArgumentCaptor<WebsiteNavigationItemDO> itemCaptor =
                ArgumentCaptor.forClass(WebsiteNavigationItemDO.class);
        verify(itemMapper, times(WebsiteNavigationPageKeyEnum.values().length)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .allSatisfy(item -> {
                    assertThat(item.getRevisionId()).isEqualTo(22L);
                    assertThat(item.getTenantId()).isEqualTo(TENANT_ID);
                });
        verify(revisionMapper, times(0)).publishDraftAtomic(any(), any(), any(), any());
    }

    @Test
    void restoreDraft_shouldRejectAnotherDraftAsHistorySource() {
        when(revisionMapper.selectByIdForTenant(24L)).thenReturn(revision(24L, "DRAFT", 3));
        when(revisionMapper.selectByIdForTenant(23L)).thenReturn(revision(23L, "DRAFT", 2));
        WebsiteNavigationRestoreReqVO request = new WebsiteNavigationRestoreReqVO();
        request.setDraftRevisionId(24L);
        request.setDraftVersion(3);
        request.setSourceRevisionId(23L);

        assertThatThrownBy(() -> service.restoreDraft(request))
                .hasMessageContaining("导航");
    }

    private static WebsiteNavigationRevisionDO revision(Long id, String status, Integer version) {
        return new WebsiteNavigationRevisionDO()
                .setId(id)
                .setSiteId(1L)
                .setLocale("en")
                .setRevisionNo(1)
                .setStatus(status)
                .setVersion(version);
    }

    private static WebsiteNavigationItemDO pageItem(Long revisionId, String itemKey, String pageKey,
                                                     String label, Integer sort) {
        return new WebsiteNavigationItemDO()
                .setRevisionId(revisionId)
                .setItemKey(itemKey)
                .setParentItemKey("")
                .setItemType("PAGE")
                .setPageKey(pageKey)
                .setLabel(label)
                .setSort(sort)
                .setVisible(true);
    }

    private static WebsiteNavigationItemDO categoryItem(Long revisionId, Long categoryId,
                                                         String label, Integer sort) {
        return new WebsiteNavigationItemDO()
                .setRevisionId(revisionId)
                .setItemKey("CATEGORY_" + categoryId)
                .setParentItemKey(WebsiteNavigationPageKeyEnum.PRODUCTS.itemKey())
                .setItemType("CATEGORY")
                .setCategoryId(categoryId)
                .setLabel(label)
                .setSort(sort)
                .setVisible(true);
    }

    private static WebsiteNavigationItemDO oakvedItem(
            Long revisionId, String itemKey, String parentItemKey, String itemType,
            String targetKey, String label, Integer sort, String styleVariant) {
        return new WebsiteNavigationItemDO()
                .setRevisionId(revisionId)
                .setItemKey(itemKey)
                .setParentItemKey(parentItemKey)
                .setItemType(itemType)
                .setTargetKey(targetKey)
                .setLabel(label)
                .setSort(sort)
                .setVisible(true)
                .setOpenMode("_self")
                .setStyleVariant(styleVariant);
    }

    private static ProductCategoryNavigationRespDTO category(Long id, String name, Long count) {
        ProductCategoryNavigationRespDTO category = new ProductCategoryNavigationRespDTO();
        category.setId(id);
        category.setParentId(1L);
        category.setName(name);
        category.setSort(10);
        category.setStatus(0);
        category.setPublishedProductCount(count);
        return category;
    }

    private static WebsiteNavigationDraftSaveReqVO draftSaveRequest(
            WebsiteNavigationRevisionDO draft, WebsiteNavigationItemSaveReqVO... additionalItems) {
        List<WebsiteNavigationItemSaveReqVO> items = new ArrayList<>();
        for (WebsiteNavigationPageKeyEnum page : WebsiteNavigationPageKeyEnum.values()) {
            WebsiteNavigationItemSaveReqVO item = new WebsiteNavigationItemSaveReqVO();
            item.setItemKey(page.itemKey());
            item.setParentItemKey("");
            item.setItemType("PAGE");
            item.setPageKey(page.getCode());
            item.setLabel(page.getDefaultLabel());
            item.setSort(page.getDefaultSort());
            item.setVisible(true);
            items.add(item);
        }
        items.addAll(Arrays.asList(additionalItems));
        WebsiteNavigationDraftSaveReqVO request = new WebsiteNavigationDraftSaveReqVO();
        request.setRevisionId(draft.getId());
        request.setSiteId(draft.getSiteId());
        request.setLocale(draft.getLocale());
        request.setVersion(draft.getVersion());
        request.setItems(items);
        return request;
    }

    private static WebsiteNavigationItemSaveReqVO categoryRequest(
            Long categoryId, String label, boolean syncCategoryName) {
        WebsiteNavigationItemSaveReqVO item = new WebsiteNavigationItemSaveReqVO();
        item.setItemType("CATEGORY");
        item.setCategoryId(categoryId);
        item.setLabel(label);
        item.setSyncCategoryName(syncCategoryName);
        item.setSort(10);
        item.setVisible(true);
        return item;
    }

}
