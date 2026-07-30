package cn.iocoder.yudao.module.product.service.spu;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_SAVE_FAIL_DELIVERY_TYPES_EMPTY;

/**
 * ERP 商品保存字段策略。
 *
 * <p>B2B 与 B2C 共用商品模型，但只校验当前业务模式实际使用的字段。B2B 不会清除已有的
 * B2C 专用数据，避免租户模式切换时丢失历史配置。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductAdminFieldPolicyService {

    private final ProductWebsiteFieldPolicyService productWebsiteFieldPolicyService;

    public void prepareForSave(ProductSpuSaveReqVO saveReqVO) {
        boolean b2b = productWebsiteFieldPolicyService.getCurrentPolicy().isB2b();
        if (!b2b && CollUtil.isEmpty(saveReqVO.getDeliveryTypes())) {
            throw exception(SPU_SAVE_FAIL_DELIVERY_TYPES_EMPTY);
        }
        if (b2b && saveReqVO.getDeliveryTypes() == null) {
            saveReqVO.setDeliveryTypes(Collections.emptyList());
        }
    }

}
