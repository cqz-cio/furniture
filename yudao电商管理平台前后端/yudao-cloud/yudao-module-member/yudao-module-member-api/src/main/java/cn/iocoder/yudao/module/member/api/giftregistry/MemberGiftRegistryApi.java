package cn.iocoder.yudao.module.member.api.giftregistry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.giftregistry.dto.MemberGiftRegistryPurchaseRecordReqDTO;
import cn.iocoder.yudao.module.member.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - Gift Registry")
public interface MemberGiftRegistryApi {

    String PREFIX = ApiConstants.PREFIX + "/gift-registry";

    @PostMapping(PREFIX + "/record-purchase")
    @Operation(summary = "记录 Gift Registry 商品购买数量")
    CommonResult<Boolean> recordPurchase(@Valid @RequestBody MemberGiftRegistryPurchaseRecordReqDTO reqDTO);

}
