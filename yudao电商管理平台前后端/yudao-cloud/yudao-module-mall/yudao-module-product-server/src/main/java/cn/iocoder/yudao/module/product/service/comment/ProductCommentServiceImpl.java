package cn.iocoder.yudao.module.product.service.comment;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.product.api.comment.dto.ProductCommentCreateReqDTO;
import cn.iocoder.yudao.module.product.controller.admin.comment.vo.ProductCommentCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.comment.vo.ProductCommentPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.comment.vo.ProductCommentReplyReqVO;
import cn.iocoder.yudao.module.product.controller.admin.comment.vo.ProductCommentUpdateVisibleReqVO;
import cn.iocoder.yudao.module.product.controller.app.comment.vo.AppCommentPageReqVO;
import cn.iocoder.yudao.module.product.convert.comment.ProductCommentConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.comment.ProductCommentDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.comment.ProductCommentMapper;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.COMMENT_BATCH_CREATE_IDEMPOTENT_CONFLICT;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.COMMENT_BATCH_CREATE_INVALID;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.COMMENT_BATCH_CREATE_PARTIAL_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.COMMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.COMMENT_ORDER_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SKU_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_NOT_EXISTS;

/**
 * 商品评论 Service 实现类
 */
@Service
@Validated
public class ProductCommentServiceImpl implements ProductCommentService {

    @Resource
    private ProductCommentMapper productCommentMapper;

    @Resource
    private ProductSpuService productSpuService;

    @Resource
    @Lazy
    private ProductSkuService productSkuService;

    @Resource
    private MemberUserApi memberUserApi;

    @Override
    public void createComment(ProductCommentCreateReqVO createReqVO) {
        ProductSkuDO sku = validateSku(createReqVO.getSkuId());
        ProductSpuDO spu = validateSpu(sku.getSpuId());
        ProductCommentDO comment = ProductCommentConvert.INSTANCE.convert(createReqVO, spu, sku);
        productCommentMapper.insert(comment);
    }

    @Override
    public Long createComment(ProductCommentCreateReqDTO createReqDTO) {
        ProductSkuDO sku = validateSku(createReqDTO.getSkuId());
        ProductSpuDO spu = validateSpu(sku.getSpuId());
        validateCommentExists(createReqDTO.getUserId(), createReqDTO.getOrderItemId());
        MemberUserRespDTO user = memberUserApi.getUser(createReqDTO.getUserId()).getCheckedData();
        ProductCommentDO comment = ProductCommentConvert.INSTANCE.convert(createReqDTO, spu, sku, user);
        productCommentMapper.insert(comment);
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createComments(List<ProductCommentCreateReqDTO> createReqDTOs) {
        if (CollUtil.isEmpty(createReqDTOs)) {
            throw exception(COMMENT_BATCH_CREATE_INVALID);
        }

        validateBatchCreateReqDTOs(createReqDTOs);
        Long userId = createReqDTOs.get(0).getUserId();
        List<Long> orderItemIds = createReqDTOs.stream().map(ProductCommentCreateReqDTO::getOrderItemId)
                .collect(Collectors.toList());
        List<ProductCommentDO> existingComments = productCommentMapper.selectListByUserIdAndOrderItemIds(userId, orderItemIds);
        if (CollUtil.isNotEmpty(existingComments)) {
            return resolveExistingBatchComments(createReqDTOs, existingComments);
        }

        MemberUserRespDTO user = memberUserApi.getUser(userId).getCheckedData();
        List<Long> commentIds = new ArrayList<>(createReqDTOs.size());
        for (ProductCommentCreateReqDTO createReqDTO : createReqDTOs) {
            ProductSkuDO sku = validateSku(createReqDTO.getSkuId());
            ProductSpuDO spu = validateSpu(sku.getSpuId());
            ProductCommentDO comment = ProductCommentConvert.INSTANCE.convert(createReqDTO, spu, sku, user);
            productCommentMapper.insert(comment);
            commentIds.add(comment.getId());
        }
        return commentIds;
    }

    @Override
    public void updateCommentVisible(ProductCommentUpdateVisibleReqVO updateReqVO) {
        validateCommentExists(updateReqVO.getId());
        productCommentMapper.updateById(new ProductCommentDO().setId(updateReqVO.getId())
                .setVisible(updateReqVO.getVisible()));
    }

    @Override
    public void replyComment(ProductCommentReplyReqVO replyVO, Long userId) {
        validateCommentExists(replyVO.getId());
        productCommentMapper.updateById(new ProductCommentDO().setId(replyVO.getId())
                .setReplyTime(LocalDateTime.now()).setReplyUserId(userId)
                .setReplyStatus(Boolean.TRUE).setReplyContent(replyVO.getReplyContent()));
    }

    @Override
    public PageResult<ProductCommentDO> getCommentPage(AppCommentPageReqVO pageVO, Boolean visible) {
        return productCommentMapper.selectPage(pageVO, visible);
    }

    @Override
    public PageResult<ProductCommentDO> getCommentPage(ProductCommentPageReqVO pageReqVO) {
        return productCommentMapper.selectPage(pageReqVO);
    }

    private List<Long> resolveExistingBatchComments(List<ProductCommentCreateReqDTO> createReqDTOs,
                                                    List<ProductCommentDO> existingComments) {
        if (existingComments.size() != createReqDTOs.size()) {
            throw exception(COMMENT_BATCH_CREATE_PARTIAL_EXISTS);
        }

        Map<Long, ProductCommentDO> existingCommentMap = new LinkedHashMap<>(existingComments.size());
        for (ProductCommentDO existingComment : existingComments) {
            existingCommentMap.put(existingComment.getOrderItemId(), existingComment);
        }
        if (existingCommentMap.size() != createReqDTOs.size()) {
            throw exception(COMMENT_BATCH_CREATE_INVALID);
        }

        List<Long> commentIds = new ArrayList<>(createReqDTOs.size());
        for (ProductCommentCreateReqDTO createReqDTO : createReqDTOs) {
            ProductCommentDO existingComment = existingCommentMap.get(createReqDTO.getOrderItemId());
            if (existingComment == null) {
                throw exception(COMMENT_BATCH_CREATE_PARTIAL_EXISTS);
            }
            if (!isSameExistingBatchPayload(createReqDTO, existingComment)) {
                throw exception(COMMENT_BATCH_CREATE_IDEMPOTENT_CONFLICT);
            }
            commentIds.add(existingComment.getId());
        }
        return commentIds;
    }

    private boolean isSameExistingBatchPayload(ProductCommentCreateReqDTO createReqDTO,
                                               ProductCommentDO existingComment) {
        return Objects.equals(createReqDTO.getUserId(), existingComment.getUserId())
                && Objects.equals(createReqDTO.getOrderId(), existingComment.getOrderId())
                && Objects.equals(createReqDTO.getOrderItemId(), existingComment.getOrderItemId())
                && Objects.equals(createReqDTO.getSkuId(), existingComment.getSkuId())
                && Objects.equals(createReqDTO.getDescriptionScores(), existingComment.getDescriptionScores())
                && Objects.equals(createReqDTO.getBenefitScores(), existingComment.getBenefitScores())
                && Objects.equals(createReqDTO.getContent(), existingComment.getContent())
                && Objects.equals(createReqDTO.getAnonymous(), existingComment.getAnonymous())
                && Objects.equals(createReqDTO.getPicUrls(), existingComment.getPicUrls());
    }

    private void validateBatchCreateReqDTOs(List<ProductCommentCreateReqDTO> createReqDTOs) {
        Long firstUserId = createReqDTOs.get(0).getUserId();
        Long firstOrderId = createReqDTOs.get(0).getOrderId();
        Set<Long> orderItemIds = new HashSet<>(createReqDTOs.size());
        for (ProductCommentCreateReqDTO createReqDTO : createReqDTOs) {
            if (!firstUserId.equals(createReqDTO.getUserId())
                    || (firstOrderId != null && !firstOrderId.equals(createReqDTO.getOrderId()))
                    || (firstOrderId == null && createReqDTO.getOrderId() != null)
                    || !orderItemIds.add(createReqDTO.getOrderItemId())) {
                throw exception(COMMENT_BATCH_CREATE_INVALID);
            }
        }
    }

    /**
     * 判断当前订单的当前商品用户是否评价过
     *
     * @param userId 用户编号
     * @param orderItemId 订单项编号
     */
    private void validateCommentExists(Long userId, Long orderItemId) {
        ProductCommentDO exist = productCommentMapper.selectByUserIdAndOrderItemId(userId, orderItemId);
        if (exist != null) {
            throw exception(COMMENT_ORDER_EXISTS);
        }
    }

    private ProductSkuDO validateSku(Long skuId) {
        ProductSkuDO sku = productSkuService.getSku(skuId, true);
        if (sku == null) {
            throw exception(SKU_NOT_EXISTS);
        }
        return sku;
    }

    private ProductSpuDO validateSpu(Long spuId) {
        ProductSpuDO spu = productSpuService.getSpu(spuId, true);
        if (spu == null) {
            throw exception(SPU_NOT_EXISTS);
        }
        return spu;
    }

    private ProductCommentDO validateCommentExists(Long id) {
        ProductCommentDO productComment = productCommentMapper.selectById(id);
        if (productComment == null) {
            throw exception(COMMENT_NOT_EXISTS);
        }
        return productComment;
    }

}
