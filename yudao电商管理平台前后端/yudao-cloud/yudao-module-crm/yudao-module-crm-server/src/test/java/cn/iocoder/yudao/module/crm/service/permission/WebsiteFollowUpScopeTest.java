package cn.iocoder.yudao.module.crm.service.permission;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.crm.controller.admin.followup.vo.CrmFollowUpRecordSaveReqVO;
import cn.iocoder.yudao.module.crm.dal.dataobject.followup.CrmFollowUpRecordDO;
import cn.iocoder.yudao.module.crm.dal.mysql.followup.CrmFollowUpRecordMapper;
import cn.iocoder.yudao.module.crm.service.followup.CrmFollowUpRecordServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebsiteFollowUpScopeTest {
    @Test void websiteOperatorCannotDeleteOrMutateRelatedSalesData() {
        var access = mock(WebsiteInquiryAccess.class);
        when(access.isWebsiteOnlyOperator()).thenReturn(true);
        var mapper = mock(CrmFollowUpRecordMapper.class);
        var service = new CrmFollowUpRecordServiceImpl();
        ReflectionTestUtils.setField(service, "websiteInquiryAccess", access);
        ReflectionTestUtils.setField(service, "crmFollowUpRecordMapper", mapper);
        assertThrows(ServiceException.class, () -> service.deleteFollowUpRecord(1L, 2L));
        assertThrows(ServiceException.class, () -> service.createFollowUpRecord(
                new CrmFollowUpRecordSaveReqVO().setBusinessIds(List.of(99L))));
        assertThrows(ServiceException.class, () -> service.createFollowUpRecord(
                new CrmFollowUpRecordSaveReqVO().setContactIds(List.of(88L))));
        verifyNoInteractions(mapper);
    }
    @Test void singleRecordReadRequiresWebsiteScopeToo() {
        var access = mock(WebsiteInquiryAccess.class);
        when(access.isWebsiteOnlyOperator()).thenReturn(true);
        var mapper = mock(CrmFollowUpRecordMapper.class);
        when(mapper.selectById(1L)).thenReturn(new CrmFollowUpRecordDO().setBizType(2).setBizId(9L));
        var service = new CrmFollowUpRecordServiceImpl();
        ReflectionTestUtils.setField(service, "websiteInquiryAccess", access);
        ReflectionTestUtils.setField(service, "crmFollowUpRecordMapper", mapper);
        assertThrows(ServiceException.class, () -> service.getFollowUpRecord(1L));
    }
}
