package cn.iocoder.yudao.module.system.controller.admin.tenant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantBusinessProfileRespVO;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.enums.tenant.TenantAdminProductFieldPolicy;
import cn.iocoder.yudao.module.system.enums.tenant.TenantBusinessModeEnum;
import cn.iocoder.yudao.module.system.enums.tenant.TenantProductFieldEnum;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TenantControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private TenantController tenantController;

    @Mock
    private TenantService tenantService;

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    public void testGetCurrentTenantBusinessProfile_b2b() {
        Long effectiveTenantId = 200L;
        TenantContextHolder.setTenantId(effectiveTenantId);
        when(tenantService.getTenant(effectiveTenantId)).thenReturn(new TenantDO()
                .setId(effectiveTenantId)
                .setBusinessMode(TenantBusinessModeEnum.B2B.getCode()));

        CommonResult<TenantBusinessProfileRespVO> result =
                tenantController.getCurrentTenantBusinessProfile();

        assertEquals(0, result.getCode());
        assertEquals(effectiveTenantId, result.getData().getTenantId());
        assertEquals(TenantBusinessModeEnum.B2B.getCode(), result.getData().getBusinessMode());
        assertFalse(result.getData().getInventoryEnabled());
        assertTrue(result.getData().getWebsiteProductFields()
                .contains(TenantProductFieldEnum.SKU_CODE.getCode()));
        assertFalse(result.getData().getWebsiteProductFields()
                .contains(TenantProductFieldEnum.PRICE.getCode()));
        assertEquals(TenantAdminProductFieldPolicy.INTERNAL,
                result.getData().getProductFieldStates().get(TenantProductFieldEnum.PRICE.getCode()));
        assertEquals(TenantAdminProductFieldPolicy.NOT_APPLICABLE,
                result.getData().getProductFieldStates().get(TenantProductFieldEnum.MARKET_PRICE.getCode()));
        assertEquals(TenantAdminProductFieldPolicy.NOT_APPLICABLE,
                result.getData().getProductFieldStates().get(TenantAdminProductFieldPolicy.DELIVERY));
        verify(tenantService).getTenant(effectiveTenantId);
    }

    @Test
    public void testGetCurrentTenantBusinessProfile_b2c() {
        Long effectiveTenantId = 201L;
        TenantContextHolder.setTenantId(effectiveTenantId);
        when(tenantService.getTenant(effectiveTenantId)).thenReturn(new TenantDO()
                .setId(effectiveTenantId)
                .setBusinessMode(TenantBusinessModeEnum.B2C.getCode()));

        TenantBusinessProfileRespVO profile =
                tenantController.getCurrentTenantBusinessProfile().getData();

        assertEquals(TenantBusinessModeEnum.B2C.getCode(), profile.getBusinessMode());
        assertTrue(profile.getInventoryEnabled());
        assertEquals(TenantProductFieldEnum.values().length, profile.getWebsiteProductFields().size());
        assertEquals(TenantAdminProductFieldPolicy.WEBSITE,
                profile.getProductFieldStates().get(TenantProductFieldEnum.PRICE.getCode()));
        assertEquals(TenantAdminProductFieldPolicy.INTERNAL,
                profile.getProductFieldStates().get(TenantAdminProductFieldPolicy.COST_PRICE));
        assertEquals(TenantAdminProductFieldPolicy.WEBSITE,
                profile.getProductFieldStates().get(TenantAdminProductFieldPolicy.DELIVERY));
    }

    @Test
    public void testGetCurrentTenantBusinessProfile_notExists() {
        Long effectiveTenantId = 202L;
        TenantContextHolder.setTenantId(effectiveTenantId);

        assertServiceException(tenantController::getCurrentTenantBusinessProfile, TENANT_NOT_EXISTS);
    }

    @Test
    public void testTenantSaveReqVO_invalidBusinessMode() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        TenantSaveReqVO reqVO = new TenantSaveReqVO().setBusinessMode("INVALID");

        assertTrue(validator.validate(reqVO).stream()
                .anyMatch(violation -> "businessMode".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testTenantSaveReqVO_invalidWebsiteProductField() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        TenantSaveReqVO reqVO = new TenantSaveReqVO()
                .setBusinessMode(TenantBusinessModeEnum.B2B.getCode())
                .setWebsiteProductFields(List.of("skuCode", "unknownField"));

        assertTrue(validator.validate(reqVO).stream()
                .anyMatch(violation -> "websiteProductFieldsValid"
                        .equals(violation.getPropertyPath().toString())));
    }

}
