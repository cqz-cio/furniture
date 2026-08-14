package cn.iocoder.yudao.module.trade.api.cart;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.service.cart.CartService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class CartApiImpl implements CartApi {

    @Resource
    private CartService cartService;

    @Override
    public CommonResult<Boolean> deleteCartBySpuId(Long spuId) {
        cartService.deleteCartBySpuId(spuId);
        return success(true);
    }

}
