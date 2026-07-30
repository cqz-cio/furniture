package cn.iocoder.yudao.module.crm.service.clue;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmClueTransformRespVO;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.contact.CrmContactDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.contact.CrmContactMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.yudao.module.crm.enums.clue.CrmInquiryProcessStatusEnum;
import cn.iocoder.yudao.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.yudao.module.crm.service.customer.bo.CrmCustomerCreateReqBO;
import cn.iocoder.yudao.module.crm.service.followup.CrmFollowUpRecordService;
import cn.iocoder.yudao.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.yudao.module.crm.service.permission.bo.CrmPermissionCreateReqBO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmClueServiceImplTest {

    private static final Long USER_ID = 101L;
    private static final Long INQUIRY_ID = 7001L;
    private static final Long CUSTOMER_ID = 8001L;
    private static final Long CONTACT_ID = 9001L;

    @InjectMocks
    private CrmClueServiceImpl clueService;

    @Mock
    private CrmClueMapper clueMapper;
    @Mock
    private CrmCustomerMapper customerMapper;
    @Mock
    private CrmContactMapper contactMapper;
    @Mock
    private CrmCustomerService customerService;
    @Mock
    private CrmPermissionService crmPermissionService;
    @Mock
    private CrmFollowUpRecordService followUpRecordService;
    @Mock
    private AdminUserApi adminUserApi;

    @Test
    void createWebsiteInquiry_persistsAlignedFieldsAndPermission() {
        CrmWebsiteInquiryCreateReqDTO reqDTO = createWebsiteInquiryReqDTO();
        doAnswer(invocation -> {
            invocation.<CrmClueDO>getArgument(0).setId(INQUIRY_ID);
            return 1;
        }).when(clueMapper).insert(any(CrmClueDO.class));

        CrmWebsiteInquiryCreateRespDTO result = clueService.createWebsiteInquiry(reqDTO);

        assertEquals(INQUIRY_ID, result.getInquiryId());
        assertTrue(result.getCreated());
        ArgumentCaptor<CrmClueDO> inquiryCaptor = ArgumentCaptor.forClass(CrmClueDO.class);
        verify(clueMapper).insert(inquiryCaptor.capture());
        CrmClueDO inquiry = inquiryCaptor.getValue();
        assertEquals("Northstar Interiors", inquiry.getCompanyName());
        assertEquals("Alex Morgan", inquiry.getContactName());
        assertEquals("alex@example.com", inquiry.getEmail());
        assertEquals("+44", inquiry.getCountryCode());
        assertEquals("7700 900123", inquiry.getTelephone());
        assertEquals("Hotel dining chair project", inquiry.getInquirySubject());
        assertEquals(CrmInquiryProcessStatusEnum.PENDING.getStatus(), inquiry.getProcessStatus());
        assertEquals(USER_ID.toString(), inquiry.getCreator());
        assertEquals(USER_ID.toString(), inquiry.getUpdater());
        verify(crmPermissionService).createPermission(any(CrmPermissionCreateReqBO.class), eq(USER_ID));
    }

    @Test
    void createWebsiteInquiry_duplicateReturnsOriginalRecord() {
        when(clueMapper.selectByExternalInquiryId("web-001"))
                .thenReturn(new CrmClueDO().setId(INQUIRY_ID));

        CrmWebsiteInquiryCreateRespDTO result =
                clueService.createWebsiteInquiry(createWebsiteInquiryReqDTO());

        assertEquals(INQUIRY_ID, result.getInquiryId());
        assertFalse(result.getCreated());
        verify(clueMapper, never()).insert(any(CrmClueDO.class));
        verify(crmPermissionService, never()).createPermission(any(CrmPermissionCreateReqBO.class));
    }

    @Test
    void transformClue_requiresCompanyForWebsiteInquiry() {
        CrmClueDO inquiry = createInquiry().setCompanyName("");
        when(clueMapper.selectById(INQUIRY_ID)).thenReturn(inquiry);

        assertThrows(ServiceException.class, () -> clueService.transformClue(INQUIRY_ID, USER_ID));

        verify(customerService, never()).createCustomer(
                any(CrmCustomerCreateReqBO.class), anyLong());
        verify(contactMapper, never()).insert(any(CrmContactDO.class));
    }

    @Test
    void transformClue_createsCustomerAndCompanyContactWithoutDeletingInquiry() {
        CrmClueDO inquiry = createInquiry();
        when(clueMapper.selectById(INQUIRY_ID)).thenReturn(inquiry);
        when(customerMapper.selectByCustomerName("Northstar Interiors")).thenReturn(null);
        when(customerService.createCustomer(any(CrmCustomerCreateReqBO.class), eq(USER_ID)))
                .thenReturn(CUSTOMER_ID);
        when(contactMapper.selectListByCustomerId(CUSTOMER_ID)).thenReturn(Collections.emptyList());
        when(followUpRecordService.getFollowUpRecordByBiz(any(), any()))
                .thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            invocation.<CrmContactDO>getArgument(0).setId(CONTACT_ID);
            return 1;
        }).when(contactMapper).insert(any(CrmContactDO.class));

        CrmClueTransformRespVO result = clueService.transformClue(INQUIRY_ID, USER_ID);

        assertEquals(CUSTOMER_ID, result.getCustomerId());
        assertEquals(CONTACT_ID, result.getContactId());
        assertTrue(result.getCustomerCreated());
        assertTrue(result.getContactCreated());

        ArgumentCaptor<CrmCustomerCreateReqBO> customerCaptor =
                ArgumentCaptor.forClass(CrmCustomerCreateReqBO.class);
        verify(customerService).createCustomer(customerCaptor.capture(), eq(USER_ID));
        assertEquals("Northstar Interiors", customerCaptor.getValue().getName());
        assertEquals("+44 7700 900123", customerCaptor.getValue().getTelephone());

        ArgumentCaptor<CrmContactDO> contactCaptor = ArgumentCaptor.forClass(CrmContactDO.class);
        verify(contactMapper).insert(contactCaptor.capture());
        assertEquals("Alex Morgan", contactCaptor.getValue().getName());
        assertEquals(CUSTOMER_ID, contactCaptor.getValue().getCustomerId());
        assertEquals("+44 7700 900123", contactCaptor.getValue().getTelephone());
        assertTrue(contactCaptor.getValue().getMaster());

        ArgumentCaptor<CrmClueDO> inquiryUpdateCaptor = ArgumentCaptor.forClass(CrmClueDO.class);
        verify(clueMapper).updateById(inquiryUpdateCaptor.capture());
        CrmClueDO inquiryUpdate = inquiryUpdateCaptor.getValue();
        assertEquals(INQUIRY_ID, inquiryUpdate.getId());
        assertEquals(CUSTOMER_ID, inquiryUpdate.getCustomerId());
        assertEquals(CONTACT_ID, inquiryUpdate.getContactId());
        assertTrue(inquiryUpdate.getTransformStatus());
        assertEquals(CrmInquiryProcessStatusEnum.PROCESSED.getStatus(),
                inquiryUpdate.getProcessStatus());
        verify(clueMapper, never()).deleteById(INQUIRY_ID);
    }

    @Test
    void transformClue_reusesExistingCustomerAndContact() {
        CrmClueDO inquiry = createInquiry();
        CrmCustomerDO customer = new CrmCustomerDO()
                .setId(CUSTOMER_ID)
                .setName("Northstar Interiors")
                .setOwnerUserId(USER_ID);
        CrmContactDO contact = new CrmContactDO()
                .setId(CONTACT_ID)
                .setCustomerId(CUSTOMER_ID)
                .setEmail("alex@example.com");
        when(clueMapper.selectById(INQUIRY_ID)).thenReturn(inquiry);
        when(customerMapper.selectByCustomerName("Northstar Interiors")).thenReturn(customer);
        when(contactMapper.selectFirstByCustomerIdAndEmail(CUSTOMER_ID, "alex@example.com"))
                .thenReturn(contact);
        when(followUpRecordService.getFollowUpRecordByBiz(any(), any()))
                .thenReturn(Collections.emptyList());

        CrmClueTransformRespVO result = clueService.transformClue(INQUIRY_ID, USER_ID);

        assertEquals(CUSTOMER_ID, result.getCustomerId());
        assertEquals(CONTACT_ID, result.getContactId());
        assertFalse(result.getCustomerCreated());
        assertFalse(result.getContactCreated());
        verify(customerService, never()).createCustomer(
                any(CrmCustomerCreateReqBO.class), anyLong());
        verify(contactMapper, never()).insert(any(CrmContactDO.class));
    }

    private static CrmWebsiteInquiryCreateReqDTO createWebsiteInquiryReqDTO() {
        CrmWebsiteInquiryCreateReqDTO reqDTO = new CrmWebsiteInquiryCreateReqDTO();
        reqDTO.setExternalInquiryId("web-001");
        reqDTO.setOwnerUserId(USER_ID);
        reqDTO.setContactName("Alex Morgan");
        reqDTO.setEmail("Alex@Example.com");
        reqDTO.setCountryCode("+44");
        reqDTO.setPhone("7700 900123");
        reqDTO.setCompanyName("Northstar Interiors");
        reqDTO.setSubject("Hotel dining chair project");
        reqDTO.setMessage("Please quote 200 chairs.");
        reqDTO.setSourcePage("/contact?utm_source=google");
        reqDTO.setLocale("en-GB");
        reqDTO.setUtmSource("google");
        reqDTO.setUtmMedium("cpc");
        reqDTO.setUtmCampaign("hotel-2026");
        reqDTO.setSubmittedAt(LocalDateTime.of(2026, 7, 27, 12, 0));
        return reqDTO;
    }

    private static CrmClueDO createInquiry() {
        return new CrmClueDO()
                .setId(INQUIRY_ID)
                .setExternalInquiryId("web-001")
                .setName("Northstar Interiors · Hotel dining chair project")
                .setCompanyName("Northstar Interiors")
                .setContactName("Alex Morgan")
                .setEmail("alex@example.com")
                .setCountryCode("+44")
                .setTelephone("7700 900123")
                .setInquirySubject("Hotel dining chair project")
                .setSource(6)
                .setOwnerUserId(USER_ID)
                .setTransformStatus(false);
    }

}
