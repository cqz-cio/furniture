package cn.iocoder.yudao.module.product.service.comment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.product.api.comment.dto.ProductCommentCreateReqDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.comment.ProductCommentDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.comment.ProductCommentMapper;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.COMMENT_BATCH_CREATE_IDEMPOTENT_CONFLICT;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.COMMENT_BATCH_CREATE_PARTIAL_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProductCommentServiceImplBatchTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductCommentServiceImpl productCommentService;

    @Mock
    private ProductCommentMapper productCommentMapper;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private MemberUserApi memberUserApi;

    @Test
    public void testCreateComments_createsWholeBatchWhenAllCommentsDoNotExist() {
        List<ProductCommentCreateReqDTO> createReqDTOs = Arrays.asList(
                buildCreateReqDTO(100L, 201L, 301L),
                buildCreateReqDTO(100L, 202L, 302L));
        AtomicLong idGenerator = new AtomicLong(9000L);
        when(productCommentMapper.selectListByUserIdAndOrderItemIds(eq(10L), eq(Arrays.asList(201L, 202L))))
                .thenReturn(Collections.emptyList());
        when(productSkuService.getSku(301L, true)).thenReturn(buildSku(301L, 401L));
        when(productSkuService.getSku(302L, true)).thenReturn(buildSku(302L, 402L));
        when(productSpuService.getSpu(401L, true)).thenReturn(buildSpu(401L, "人体工学椅"));
        when(productSpuService.getSpu(402L, true)).thenReturn(buildSpu(402L, "台灯"));
        when(memberUserApi.getUser(10L)).thenReturn(CommonResult.success(buildUser()));
        doAnswer(invocation -> {
            ProductCommentDO comment = invocation.getArgument(0);
            comment.setId(idGenerator.incrementAndGet());
            return 1;
        }).when(productCommentMapper).insert(any(ProductCommentDO.class));

        List<Long> commentIds = productCommentService.createComments(createReqDTOs);

        assertEquals(Arrays.asList(9001L, 9002L), commentIds);
        verify(productCommentMapper, times(2)).insert(any(ProductCommentDO.class));
    }

    @Test
    public void testCreateComments_returnsExistingIdsWhenWholeBatchAlreadyExists() {
        List<ProductCommentCreateReqDTO> createReqDTOs = Arrays.asList(
                buildCreateReqDTO(100L, 201L, 301L),
                buildCreateReqDTO(100L, 202L, 302L));
        when(productCommentMapper.selectListByUserIdAndOrderItemIds(eq(10L), eq(Arrays.asList(201L, 202L))))
                .thenReturn(Arrays.asList(
                        buildMatchingExistingComment(9001L, createReqDTOs.get(0)),
                        buildMatchingExistingComment(9002L, createReqDTOs.get(1))));

        List<Long> commentIds = productCommentService.createComments(createReqDTOs);

        assertEquals(Arrays.asList(9001L, 9002L), commentIds);
        verify(productCommentMapper, never()).insert(any(ProductCommentDO.class));
        verify(memberUserApi, never()).getUser(any());
    }

    @Test
    public void testCreateComments_rejectsExistingBatchWhenRequestPayloadChanges() {
        List<ProductCommentCreateReqDTO> createReqDTOs = Arrays.asList(
                buildCreateReqDTO(100L, 201L, 301L),
                buildCreateReqDTO(100L, 202L, 302L));
        createReqDTOs.get(1).setContent("这次改成新的评价内容");
        when(productCommentMapper.selectListByUserIdAndOrderItemIds(eq(10L), eq(Arrays.asList(201L, 202L))))
                .thenReturn(Arrays.asList(
                        new ProductCommentDO().setId(9001L).setOrderItemId(201L).setUserId(10L)
                                .setOrderId(100L).setSkuId(301L).setDescriptionScores(5).setBenefitScores(5)
                                .setContent("鏁翠綋婊℃剰").setAnonymous(Boolean.FALSE).setPicUrls(Collections.emptyList()),
                        new ProductCommentDO().setId(9002L).setOrderItemId(202L).setUserId(10L)
                                .setOrderId(100L).setSkuId(302L).setDescriptionScores(5).setBenefitScores(5)
                                .setContent("鏁翠綋婊℃剰").setAnonymous(Boolean.FALSE).setPicUrls(Collections.emptyList())));

        ServiceException serviceException = assertThrows(ServiceException.class,
                () -> productCommentService.createComments(createReqDTOs));

        assertEquals(COMMENT_BATCH_CREATE_IDEMPOTENT_CONFLICT.getCode(), serviceException.getCode());
        verify(productCommentMapper, never()).insert(any(ProductCommentDO.class));
        verify(memberUserApi, never()).getUser(any());
    }

    @Test
    public void testCreateComments_rejectsPartiallyExistingComments() {
        List<ProductCommentCreateReqDTO> createReqDTOs = Arrays.asList(
                buildCreateReqDTO(100L, 201L, 301L),
                buildCreateReqDTO(100L, 202L, 302L));
        when(productCommentMapper.selectListByUserIdAndOrderItemIds(eq(10L), eq(Arrays.asList(201L, 202L))))
                .thenReturn(Collections.singletonList(
                        new ProductCommentDO().setId(9001L).setOrderItemId(201L).setUserId(10L)));

        ServiceException serviceException = assertThrows(ServiceException.class,
                () -> productCommentService.createComments(createReqDTOs));

        assertEquals(COMMENT_BATCH_CREATE_PARTIAL_EXISTS.getCode(), serviceException.getCode());
        verify(productCommentMapper, never()).insert(any(ProductCommentDO.class));
    }

    private static ProductCommentCreateReqDTO buildCreateReqDTO(Long orderId, Long orderItemId, Long skuId) {
        ProductCommentCreateReqDTO reqDTO = new ProductCommentCreateReqDTO();
        reqDTO.setSkuId(skuId);
        reqDTO.setOrderId(orderId);
        reqDTO.setOrderItemId(orderItemId);
        reqDTO.setDescriptionScores(5);
        reqDTO.setBenefitScores(5);
        reqDTO.setContent("整体满意");
        reqDTO.setPicUrls(Collections.emptyList());
        reqDTO.setAnonymous(Boolean.FALSE);
        reqDTO.setUserId(10L);
        return reqDTO;
    }

    private static ProductCommentDO buildExistingComment(Long commentId, Long orderId, Long orderItemId, Long skuId) {
        return new ProductCommentDO().setId(commentId).setOrderItemId(orderItemId).setUserId(10L)
                .setOrderId(orderId).setSkuId(skuId).setDescriptionScores(5).setBenefitScores(5)
                .setContent("鏁翠綋婊℃剰").setAnonymous(Boolean.FALSE).setPicUrls(Collections.emptyList());
    }

    private static ProductCommentDO buildMatchingExistingComment(Long commentId, ProductCommentCreateReqDTO reqDTO) {
        return new ProductCommentDO().setId(commentId).setOrderItemId(reqDTO.getOrderItemId()).setUserId(reqDTO.getUserId())
                .setOrderId(reqDTO.getOrderId()).setSkuId(reqDTO.getSkuId())
                .setDescriptionScores(reqDTO.getDescriptionScores()).setBenefitScores(reqDTO.getBenefitScores())
                .setContent(reqDTO.getContent()).setAnonymous(reqDTO.getAnonymous()).setPicUrls(reqDTO.getPicUrls());
    }

    private static ProductSkuDO buildSku(Long skuId, Long spuId) {
        ProductSkuDO sku = new ProductSkuDO();
        sku.setId(skuId);
        sku.setSpuId(spuId);
        sku.setPicUrl("https://example.com/sku.png");
        sku.setProperties(Collections.emptyList());
        return sku;
    }

    private static ProductSpuDO buildSpu(Long spuId, String name) {
        ProductSpuDO spu = new ProductSpuDO();
        spu.setId(spuId);
        spu.setName(name);
        return spu;
    }

    private static MemberUserRespDTO buildUser() {
        MemberUserRespDTO user = new MemberUserRespDTO();
        user.setId(10L);
        user.setNickname("测试用户");
        user.setAvatar("https://example.com/avatar.png");
        return user;
    }

}
