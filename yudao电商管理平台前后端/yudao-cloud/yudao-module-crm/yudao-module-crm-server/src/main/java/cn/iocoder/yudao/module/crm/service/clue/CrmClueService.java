package cn.iocoder.yudao.module.crm.service.clue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmCluePageReqVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmClueSaveReqVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmClueTransferReqVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmClueTransformRespVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmInquiryProcessStatusUpdateReqVO;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmInquirySummaryRespVO;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import jakarta.validation.Valid;

import java.time.LocalDateTime;

/**
 * 线索 Service 接口
 *
 * @author Wanwan
 */
public interface CrmClueService {

    /**
     * 创建线索
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createClue(@Valid CrmClueSaveReqVO createReqVO);

    /**
     * 创建官网询盘，外部询盘编号重复时返回原记录。
     */
    CrmWebsiteInquiryCreateRespDTO createWebsiteInquiry(@Valid CrmWebsiteInquiryCreateReqDTO reqDTO);

    /**
     * 更新线索
     *
     * @param updateReqVO 更新信息
     */
    void updateClue(@Valid CrmClueSaveReqVO updateReqVO);

    /**
     * 更新线索相关的跟进信息
     *
     * @param id 编号
     * @param contactNextTime 下次联系时间
     * @param contactLastContent 最后联系内容
     */
    void updateClueFollowUp(Long id, LocalDateTime contactNextTime, String contactLastContent);

    /**
     * 更新询盘处理状态。
     */
    void updateInquiryProcessStatus(@Valid CrmInquiryProcessStatusUpdateReqVO reqVO);

    /**
     * 删除线索
     *
     * @param id 编号
     */
    void deleteClue(Long id);

    /**
     * 获得线索
     *
     * @param id 编号
     * @return 线索
     */
    CrmClueDO getClue(Long id);

    /**
     * 获得线索分页
     *
     * @param pageReqVO 分页查询
     * @param userId    用户编号
     * @return 线索分页
     */
    PageResult<CrmClueDO> getCluePage(CrmCluePageReqVO pageReqVO, Long userId);

    /**
     * 线索转移
     *
     * @param reqVO  请求
     * @param userId 用户编号
     */
    void transferClue(CrmClueTransferReqVO reqVO, Long userId);

    /**
     * 线索转化为客户
     *
     * @param id  线索编号
     * @param userId 用户编号
     */
    CrmClueTransformRespVO transformClue(Long id, Long userId);

    /**
     * 获得当前用户可见的询盘状态汇总。
     */
    CrmInquirySummaryRespVO getInquirySummary(Long userId);

    /**
     * 获得分配给我的、待跟进的线索数量
     *
     * @param userId 用户编号
     * @return 数量
     */
    Long getFollowClueCount(Long userId);

}
