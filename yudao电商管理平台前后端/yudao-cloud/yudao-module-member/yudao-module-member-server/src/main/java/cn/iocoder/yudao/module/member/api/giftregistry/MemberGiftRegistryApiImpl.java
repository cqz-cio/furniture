package cn.iocoder.yudao.module.member.api.giftregistry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.giftregistry.dto.MemberGiftRegistryPurchaseRecordReqDTO;
import cn.iocoder.yudao.module.member.service.giftregistry.MemberGiftRegistryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class MemberGiftRegistryApiImpl implements MemberGiftRegistryApi {

    @Resource
    private MemberGiftRegistryService memberGiftRegistryService;

    @Override
    public CommonResult<Boolean> recordPurchase(MemberGiftRegistryPurchaseRecordReqDTO reqDTO) {
        memberGiftRegistryService.recordPurchasedItems(reqDTO.getItems());
        return success(true);
    }

    @Override
    public CommonResult<Boolean> deleteItemsBySpuId(Long spuId) {
        memberGiftRegistryService.deleteItemsBySpuId(spuId);
        return success(true);
    }

}
