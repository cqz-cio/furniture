package cn.iocoder.yudao.module.product.api.comment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.api.comment.dto.ProductCommentBatchCreateReqDTO;
import cn.iocoder.yudao.module.product.api.comment.dto.ProductCommentCreateReqDTO;
import cn.iocoder.yudao.module.product.service.comment.ProductCommentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商品评论 API 实现类
 */
@RestController
@Validated
public class ProductCommentApiImpl implements ProductCommentApi {

    @Resource
    private ProductCommentService productCommentService;

    @Override
    public CommonResult<Long> createComment(ProductCommentCreateReqDTO createReqDTO) {
        return success(productCommentService.createComment(createReqDTO));
    }

    @Override
    public CommonResult<List<Long>> createComments(ProductCommentBatchCreateReqDTO createReqDTO) {
        return success(productCommentService.createComments(createReqDTO.getComments()));
    }

}
