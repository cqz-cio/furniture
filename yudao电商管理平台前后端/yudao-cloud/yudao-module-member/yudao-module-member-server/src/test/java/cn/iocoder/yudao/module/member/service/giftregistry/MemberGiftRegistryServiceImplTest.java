package cn.iocoder.yudao.module.member.service.giftregistry;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.controller.app.giftregistry.vo.AppGiftRegistryCreateReqVO;
import cn.iocoder.yudao.module.member.controller.app.giftregistry.vo.AppGiftRegistryItemAddReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryDO;
import cn.iocoder.yudao.module.member.dal.mysql.giftregistry.MemberGiftRegistryItemMapper;
import cn.iocoder.yudao.module.member.dal.mysql.giftregistry.MemberGiftRegistryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.GIFT_REGISTRY_ACCESS_DENIED;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.GIFT_REGISTRY_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MemberGiftRegistryServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private MemberGiftRegistryServiceImpl giftRegistryService;

    @Mock
    private MemberGiftRegistryMapper giftRegistryMapper;
    @Mock
    private MemberGiftRegistryItemMapper giftRegistryItemMapper;

    @Test
    public void testCreateGiftRegistry_generatesPublicCodeAndPersistsOwner() {
        AppGiftRegistryCreateReqVO reqVO = new AppGiftRegistryCreateReqVO();
        reqVO.setRegistrantName("Avery Stone");
        reqVO.setCoRegistrantName("Morgan Vale");
        reqVO.setEmail("avery@example.com");
        reqVO.setEventType("Wedding");
        reqVO.setEventDate(LocalDate.of(2026, 10, 1));
        reqVO.setVisibility(MemberGiftRegistryService.VISIBILITY_PUBLIC);

        MemberGiftRegistryDO result = giftRegistryService.createGiftRegistry(18L, reqVO);

        assertEquals(18L, result.getUserId());
        assertEquals(MemberGiftRegistryService.STATUS_ACTIVE, result.getStatus());
        verify(giftRegistryMapper).insert(org.mockito.ArgumentMatchers.<MemberGiftRegistryDO>argThat(registry ->
                Long.valueOf(18L).equals(registry.getUserId())
                        && registry.getPublicCode().startsWith("registry-")
                        && "Avery Stone".equals(registry.getRegistrantName())
                        && MemberGiftRegistryService.VISIBILITY_PUBLIC.equals(registry.getVisibility())));
    }

    @Test
    public void testAddGiftRegistryItem_whenNotOwner() {
        when(giftRegistryMapper.selectById(eq(88L))).thenReturn(new MemberGiftRegistryDO()
                .setId(88L)
                .setUserId(18L));

        AppGiftRegistryItemAddReqVO reqVO = new AppGiftRegistryItemAddReqVO();
        reqVO.setRegistryId(88L);
        reqVO.setSpuId(1001L);
        reqVO.setSkuId(2001L);
        reqVO.setProductName("Walnut Single Sofa");
        reqVO.setQuantityRequested(1);

        assertServiceException(() -> giftRegistryService.addGiftRegistryItem(99L, reqVO),
                GIFT_REGISTRY_ACCESS_DENIED);
    }

    @Test
    public void testGetPublicGiftRegistry_onlyActivePublicRegistry() {
        when(giftRegistryMapper.selectPublicByPublicCode(eq("hidden-code"))).thenReturn(null);

        assertServiceException(() -> giftRegistryService.getPublicGiftRegistry("hidden-code"),
                GIFT_REGISTRY_NOT_EXISTS);
    }

    @Test
    public void testDeleteItemsBySpuId() {
        giftRegistryService.deleteItemsBySpuId(1001L);

        verify(giftRegistryItemMapper).deleteBySpuId(1001L);
    }
}
