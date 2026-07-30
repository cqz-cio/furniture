package cn.iocoder.yudao.module.crm.service.clue;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmCluePageReqVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmClueSaveReqVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmClueTransferReqVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmClueTransformRespVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmInquiryProcessStatusUpdateReqVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmInquirySummaryRespVO;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.contact.CrmContactDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.followup.CrmFollowUpRecordDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.contact.CrmContactMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.yudao.module.crm.enums.clue.CrmInquiryProcessStatusEnum;
import cn.iocoder.yudao.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.yudao.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.yudao.module.crm.framework.permission.core.annotations.CrmPermission;
import cn.iocoder.yudao.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.yudao.module.crm.service.customer.bo.CrmCustomerCreateReqBO;
import cn.iocoder.yudao.module.crm.service.followup.CrmFollowUpRecordService;
import cn.iocoder.yudao.module.crm.service.followup.bo.CrmFollowUpCreateReqBO;
import cn.iocoder.yudao.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.yudao.module.crm.service.permission.bo.CrmPermissionCreateReqBO;
import cn.iocoder.yudao.module.crm.service.permission.bo.CrmPermissionTransferReqBO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.service.impl.DiffParseFunction;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.singleton;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.skipPermissionCheck;
import static cn.iocoder.yudao.module.crm.enums.ErrorCodeConstants.CLUE_NOT_EXISTS;
import static cn.iocoder.yudao.module.crm.enums.ErrorCodeConstants.CLUE_TRANSFORM_FAIL_ALREADY;
import static cn.iocoder.yudao.module.crm.enums.ErrorCodeConstants.INQUIRY_COMPANY_NAME_REQUIRED;
import static cn.iocoder.yudao.module.crm.enums.LogRecordConstants.*;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_NOT_EXISTS;

/**
 * 线索 Service 实现类
 *
 * @author Wanwan
 */
@Service
@Validated
public class CrmClueServiceImpl implements CrmClueService {

    @Resource
    private CrmClueMapper clueMapper;
    @Resource
    private CrmCustomerMapper customerMapper;
    @Resource
    private CrmContactMapper contactMapper;

    @Resource
    private CrmCustomerService customerService;
    @Resource
    private CrmPermissionService crmPermissionService;
    @Resource
    private CrmFollowUpRecordService followUpRecordService;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CLUE_TYPE, subType = CRM_CLUE_CREATE_SUB_TYPE, bizNo = "{{#clue.id}}",
            success = CRM_CLUE_CREATE_SUCCESS)
    public Long createClue(CrmClueSaveReqVO createReqVO) {
        // 1.1 校验关联数据
        validateRelationDataExists(createReqVO);
        // 1.2 校验负责人是否存在
        adminUserApi.validateUser(createReqVO.getOwnerUserId());

        // 2. 插入线索
        CrmClueDO clue = BeanUtils.toBean(createReqVO, CrmClueDO.class);
        clueMapper.insert(clue);

        // 3. 创建数据权限
        CrmPermissionCreateReqBO createReqBO = new CrmPermissionCreateReqBO().setBizType(CrmBizTypeEnum.CRM_CLUE.getType())
                .setBizId(clue.getId()).setUserId(clue.getOwnerUserId()).setLevel(CrmPermissionLevelEnum.OWNER.getLevel());
        crmPermissionService.createPermission(createReqBO);

        // 4. 记录操作日志上下文
        LogRecordContext.putVariable("clue", clue);
        return clue.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmWebsiteInquiryCreateRespDTO createWebsiteInquiry(CrmWebsiteInquiryCreateReqDTO reqDTO) {
        String externalInquiryId = normalize(reqDTO.getExternalInquiryId());
        CrmClueDO existing = clueMapper.selectByExternalInquiryId(externalInquiryId);
        if (existing != null) {
            return new CrmWebsiteInquiryCreateRespDTO(existing.getId(), false);
        }

        adminUserApi.validateUser(reqDTO.getOwnerUserId());
        String contactName = normalize(reqDTO.getContactName());
        String companyName = normalize(reqDTO.getCompanyName());
        String subject = normalize(reqDTO.getSubject());
        CrmClueDO inquiry = new CrmClueDO()
                .setName(buildInquiryDisplayName(companyName, contactName, subject))
                .setExternalInquiryId(externalInquiryId)
                .setContactName(contactName)
                .setCompanyName(companyName)
                .setCountryCode(normalize(reqDTO.getCountryCode()))
                .setInquirySubject(subject)
                .setInquiryMessage(normalizeMultiline(reqDTO.getMessage()))
                .setSourcePage(normalize(reqDTO.getSourcePage()))
                .setLocale(normalize(reqDTO.getLocale()))
                .setUtmSource(normalize(reqDTO.getUtmSource()))
                .setUtmMedium(normalize(reqDTO.getUtmMedium()))
                .setUtmCampaign(normalize(reqDTO.getUtmCampaign()))
                .setSubmittedAt(reqDTO.getSubmittedAt())
                .setProcessStatus(CrmInquiryProcessStatusEnum.PENDING.getStatus())
                .setOwnerUserId(reqDTO.getOwnerUserId())
                .setFollowUpStatus(false)
                .setTransformStatus(false)
                .setTelephone(normalize(reqDTO.getPhone()))
                .setEmail(normalize(reqDTO.getEmail()).toLowerCase(Locale.ROOT))
                .setSource(6); // CRM 客户来源：线上咨询
        // 官网接口没有登录态，显式使用租户联系人作为审计操作人，避免公共接口插入时审计字段为空。
        String auditUserId = reqDTO.getOwnerUserId().toString();
        inquiry.setCreator(auditUserId);
        inquiry.setUpdater(auditUserId);
        try {
            clueMapper.insert(inquiry);
        } catch (DuplicateKeyException ex) {
            CrmClueDO duplicated = clueMapper.selectByExternalInquiryId(externalInquiryId);
            if (duplicated != null) {
                return new CrmWebsiteInquiryCreateRespDTO(duplicated.getId(), false);
            }
            throw ex;
        }

        crmPermissionService.createPermission(new CrmPermissionCreateReqBO()
                .setBizType(CrmBizTypeEnum.CRM_CLUE.getType())
                .setBizId(inquiry.getId())
                .setUserId(inquiry.getOwnerUserId())
                .setLevel(CrmPermissionLevelEnum.OWNER.getLevel()), reqDTO.getOwnerUserId());
        return new CrmWebsiteInquiryCreateRespDTO(inquiry.getId(), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CLUE_TYPE, subType = CRM_CLUE_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
            success = CRM_CLUE_UPDATE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CLUE, bizId = "#updateReqVO.id", level = CrmPermissionLevelEnum.OWNER)
    public void updateClue(CrmClueSaveReqVO updateReqVO) {
        Assert.notNull(updateReqVO.getId(), "线索编号不能为空");
        // 1.1 校验线索是否存在
        CrmClueDO oldClue = validateClueExists(updateReqVO.getId());
        // 1.2 校验关联数据
        validateRelationDataExists(updateReqVO);

        // 2. 更新线索
        CrmClueDO updateObj = BeanUtils.toBean(updateReqVO, CrmClueDO.class);
        if (!normalize(oldClue.getExternalInquiryId()).isEmpty()) {
            updateObj.setContactName(normalize(updateReqVO.getContactName()))
                    .setCompanyName(normalize(updateReqVO.getCompanyName()))
                    .setCountryCode(normalize(updateReqVO.getCountryCode()))
                    .setInquirySubject(normalize(updateReqVO.getInquirySubject()))
                    .setInquiryMessage(normalizeMultiline(updateReqVO.getInquiryMessage()))
                    .setTelephone(normalize(updateReqVO.getTelephone()))
                    .setEmail(normalize(updateReqVO.getEmail()).toLowerCase(Locale.ROOT))
                    .setName(buildInquiryDisplayName(
                            normalize(updateReqVO.getCompanyName()),
                            normalize(updateReqVO.getContactName()),
                            normalize(updateReqVO.getInquirySubject())));
        }
        clueMapper.updateById(updateObj);

        // 3. 记录操作日志上下文
        updateReqVO.setOwnerUserId(oldClue.getOwnerUserId()); // 避免操作日志出现“删除负责人”的情况
        LogRecordContext.putVariable(DiffParseFunction.OLD_OBJECT, BeanUtils.toBean(oldClue, CrmClueSaveReqVO.class));
        LogRecordContext.putVariable("clueName", oldClue.getName());
    }

    private void validateRelationDataExists(CrmClueSaveReqVO reqVO) {
        // 校验负责人
        if (Objects.nonNull(reqVO.getOwnerUserId()) &&
                Objects.isNull(adminUserApi.getUser(reqVO.getOwnerUserId()).getCheckedData())) {
            throw exception(USER_NOT_EXISTS);
        }
    }

    @Override
    @LogRecord(type = CRM_CLUE_TYPE, subType = CRM_CLUE_FOLLOW_UP_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_CLUE_FOLLOW_UP_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CLUE, bizId = "#id", level = CrmPermissionLevelEnum.WRITE)
    public void updateClueFollowUp(Long id, LocalDateTime contactNextTime, String contactLastContent) {
        // 校验线索是否存在
        CrmClueDO oldClue = validateClueExists(id);

        // 更新线索
        clueMapper.updateById(new CrmClueDO().setId(id).setFollowUpStatus(true).setContactNextTime(contactNextTime)
                .setContactLastTime(LocalDateTime.now()).setContactLastContent(contactLastContent));

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("clueName", oldClue.getName());
    }

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CLUE, bizId = "#reqVO.id",
            level = CrmPermissionLevelEnum.WRITE)
    public void updateInquiryProcessStatus(CrmInquiryProcessStatusUpdateReqVO reqVO) {
        validateClueExists(reqVO.getId());
        LocalDateTime processedAt = CrmInquiryProcessStatusEnum.isFinished(reqVO.getProcessStatus())
                ? LocalDateTime.now() : null;
        clueMapper.updateProcessStatus(reqVO.getId(), reqVO.getProcessStatus(),
                processedAt, reqVO.getRemark() == null ? null : normalizeMultiline(reqVO.getRemark()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CLUE_TYPE, subType = CRM_CLUE_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_CLUE_DELETE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CLUE, bizId = "#id", level = CrmPermissionLevelEnum.OWNER)
    public void deleteClue(Long id) {
        // 1. 校验存在
        CrmClueDO clue = validateClueExists(id);

        // 2. 删除
        clueMapper.deleteById(id);

        // 3. 删除数据权限
        crmPermissionService.deletePermission(CrmBizTypeEnum.CRM_CLUE.getType(), id);

        // 4. 删除跟进
        followUpRecordService.deleteFollowUpRecordByBiz(CrmBizTypeEnum.CRM_CLUE.getType(), id);

        // 5. 记录操作日志上下文
        LogRecordContext.putVariable("clueName", clue.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CLUE_TYPE, subType = CRM_CLUE_TRANSFER_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = CRM_CLUE_TRANSFER_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CLUE, bizId = "#reqVO.id", level = CrmPermissionLevelEnum.OWNER)
    public void transferClue(CrmClueTransferReqVO reqVO, Long userId) {
        // 1 校验线索是否存在
        CrmClueDO clue = validateClueExists(reqVO.getId());

        // 2.1 数据权限转移
        crmPermissionService.transferPermission(new CrmPermissionTransferReqBO(userId, CrmBizTypeEnum.CRM_CLUE.getType(),
                        reqVO.getId(), reqVO.getNewOwnerUserId(), reqVO.getOldOwnerPermissionLevel()));
        // 2.2 设置新的负责人
        clueMapper.updateById(new CrmClueDO().setId(reqVO.getId()).setOwnerUserId(reqVO.getNewOwnerUserId()));

        // 3. 记录转移日志
        LogRecordContext.putVariable("clue", clue);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CLUE_TYPE, subType = CRM_CLUE_TRANSLATE_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_CLUE_TRANSLATE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CLUE, bizId = "#id", level = CrmPermissionLevelEnum.OWNER)
    public CrmClueTransformRespVO transformClue(Long id, Long userId) {
        // 1.1 校验询盘存在
        CrmClueDO clue = validateClueExists(id);
        // 1.2 已转化时禁止重复操作
        if (Boolean.TRUE.equals(clue.getTransformStatus())) {
            throw exception(CLUE_TRANSFORM_FAIL_ALREADY);
        }

        // 官网询盘必须先补齐公司名称，手工线索继续兼容原来的“线索名称即客户名称”规则。
        String companyName = normalize(clue.getCompanyName());
        if (companyName.isEmpty()) {
            if (!normalize(clue.getExternalInquiryId()).isEmpty()) {
                throw exception(INQUIRY_COMPANY_NAME_REQUIRED);
            }
            companyName = normalize(clue.getName());
        }
        // 跨租户访问的平台管理员并不属于被访问租户。此时沿用询盘负责人，
        // 避免新客户和联系人被错误归属给租户内不存在的平台账号。
        Long conversionOwnerUserId = skipPermissionCheck() && clue.getOwnerUserId() != null
                ? clue.getOwnerUserId() : userId;

        // 2.1 按公司名称复用客户；没有匹配项时创建客户档案。
        CrmCustomerDO customer = customerMapper.selectByCustomerName(companyName);
        boolean customerCreated = customer == null;
        Long customerId;
        Long contactOwnerUserId;
        if (customerCreated) {
            CrmCustomerCreateReqBO customerReqBO = new CrmCustomerCreateReqBO();
            customerReqBO.setName(companyName);
            customerReqBO.setFollowUpStatus(false);
            customerReqBO.setLockStatus(false);
            customerReqBO.setDealStatus(false);
            customerReqBO.setSource(clue.getSource());
            customerReqBO.setTelephone(buildFullPhone(clue.getCountryCode(), clue.getTelephone()));
            customerReqBO.setEmail(normalize(clue.getEmail()).toLowerCase(Locale.ROOT));
            customerReqBO.setRemark(buildConversionRemark(clue));
            customerId = customerService.createCustomer(customerReqBO, conversionOwnerUserId);
            contactOwnerUserId = conversionOwnerUserId;
        } else {
            customerId = customer.getId();
            contactOwnerUserId = customer.getOwnerUserId() != null
                    ? customer.getOwnerUserId() : conversionOwnerUserId;
        }

        // 2.2 按“客户 + 邮箱”优先查重，再以电话兜底；没有匹配项时创建联系人。
        String email = normalize(clue.getEmail()).toLowerCase(Locale.ROOT);
        String fullPhone = buildFullPhone(clue.getCountryCode(), clue.getTelephone());
        CrmContactDO contact = email.isEmpty() ? null
                : contactMapper.selectFirstByCustomerIdAndEmail(customerId, email);
        if (contact == null && !fullPhone.isEmpty()) {
            contact = contactMapper.selectFirstByCustomerIdAndTelephone(customerId, fullPhone);
        }
        boolean contactCreated = contact == null;
        Long contactId;
        if (contactCreated) {
            boolean firstContact = CollUtil.isEmpty(contactMapper.selectListByCustomerId(customerId));
            contact = new CrmContactDO()
                    .setName(defaultIfBlank(clue.getContactName(), clue.getName()))
                    .setCustomerId(customerId)
                    .setOwnerUserId(contactOwnerUserId)
                    .setTelephone(fullPhone)
                    .setEmail(email)
                    .setMaster(firstContact)
                    .setRemark(buildConversionRemark(clue));
            contactMapper.insert(contact);
            contactId = contact.getId();
            crmPermissionService.createPermission(new CrmPermissionCreateReqBO()
                    .setBizType(CrmBizTypeEnum.CRM_CONTACT.getType())
                    .setBizId(contactId)
                    .setUserId(contactOwnerUserId)
                    .setLevel(CrmPermissionLevelEnum.OWNER.getLevel()));
        } else {
            contactId = contact.getId();
        }

        // 2.3 保留原始询盘，只建立客户、联系人关联并完成处理。
        clueMapper.updateById(new CrmClueDO()
                .setId(id)
                .setTransformStatus(Boolean.TRUE)
                .setCustomerId(customerId)
                .setContactId(contactId)
                .setProcessStatus(CrmInquiryProcessStatusEnum.PROCESSED.getStatus())
                .setProcessedAt(LocalDateTime.now()));

        // 2.4 兼容已有手工跟进记录，复制到客户档案。
        List<CrmFollowUpRecordDO> followUpRecords = followUpRecordService.getFollowUpRecordByBiz(
                CrmBizTypeEnum.CRM_CLUE.getType(), singleton(clue.getId()));
        if (CollUtil.isNotEmpty(followUpRecords)) {
            followUpRecordService.createFollowUpRecordBatch(convertList(followUpRecords, record ->
                    BeanUtils.toBean(record, CrmFollowUpCreateReqBO.class)
                            .setBizType(CrmBizTypeEnum.CRM_CUSTOMER.getType()).setBizId(customerId)));
        }

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("clueName", clue.getName());
        return new CrmClueTransformRespVO(customerId, contactId, customerCreated, contactCreated);
    }

    private CrmClueDO validateClueExists(Long id) {
        CrmClueDO crmClueDO = clueMapper.selectById(id);
        if (crmClueDO == null) {
            throw exception(CLUE_NOT_EXISTS);
        }
        return crmClueDO;
    }

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CLUE, bizId = "#id", level = CrmPermissionLevelEnum.READ)
    public CrmClueDO getClue(Long id) {
        return clueMapper.selectById(id);
    }

    @Override
    public PageResult<CrmClueDO> getCluePage(CrmCluePageReqVO pageReqVO, Long userId) {
        return clueMapper.selectPage(pageReqVO, userId);
    }

    @Override
    public CrmInquirySummaryRespVO getInquirySummary(Long userId) {
        CrmInquirySummaryRespVO summary = new CrmInquirySummaryRespVO();
        summary.setTotalCount(clueMapper.selectInquiryCount(userId, null));
        summary.setPendingCount(clueMapper.selectInquiryCount(
                userId, CrmInquiryProcessStatusEnum.PENDING.getStatus()));
        summary.setProcessingCount(clueMapper.selectInquiryCount(
                userId, CrmInquiryProcessStatusEnum.PROCESSING.getStatus()));
        summary.setProcessedCount(clueMapper.selectInquiryCount(
                userId, CrmInquiryProcessStatusEnum.PROCESSED.getStatus()));
        summary.setInvalidCount(clueMapper.selectInquiryCount(
                userId, CrmInquiryProcessStatusEnum.INVALID.getStatus()));
        return summary;
    }

    @Override
    public Long getFollowClueCount(Long userId) {
        return clueMapper.selectCountByFollow(userId);
    }

    private static String buildInquiryDisplayName(String companyName, String contactName, String subject) {
        String prefix = companyName.isEmpty() ? contactName : companyName;
        return limit(prefix + " · " + subject, 128);
    }

    private static String buildConversionRemark(CrmClueDO clue) {
        String reference = normalize(clue.getExternalInquiryId());
        if (reference.isEmpty()) {
            reference = String.valueOf(clue.getId());
        }
        return limit("由官网询盘 " + reference + " 生成；主题：" + normalize(clue.getInquirySubject()), 500);
    }

    private static String buildFullPhone(String countryCode, String phone) {
        String normalizedCountryCode = normalize(countryCode);
        String normalizedPhone = normalize(phone);
        if (normalizedCountryCode.isEmpty()) {
            return normalizedPhone;
        }
        if (normalizedPhone.isEmpty()) {
            return normalizedCountryCode;
        }
        return normalizedCountryCode + " " + normalizedPhone;
    }

    private static String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? normalize(fallback) : normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value
                .replaceAll("[\\p{Cntrl}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeMultiline(String value) {
        return value == null ? "" : value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]+", " ")
                .trim();
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

}
