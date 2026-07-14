package cn.iocoder.yudao.module.member.controller.admin.giftregistry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.giftregistry.vo.MemberGiftRegistryPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.giftregistry.vo.MemberGiftRegistryStatusUpdateReqVO;
import cn.iocoder.yudao.module.member.controller.app.giftregistry.vo.AppGiftRegistryItemRespVO;
import cn.iocoder.yudao.module.member.controller.app.giftregistry.vo.AppGiftRegistryRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryDO;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryItemDO;
import cn.iocoder.yudao.module.member.service.giftregistry.MemberGiftRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

@Tag(name = "Admin - Gift Registry")
@RestController
@RequestMapping("/member/gift-registry")
@Validated
public class MemberGiftRegistryController {

    @Resource
    private MemberGiftRegistryService giftRegistryService;

    @GetMapping("/page")
    @Operation(summary = "Get gift registry page")
    @PreAuthorize("@ss.hasPermission('member:gift-registry:query')")
    public CommonResult<PageResult<AppGiftRegistryRespVO>> getGiftRegistryPage(@Valid MemberGiftRegistryPageReqVO pageReqVO) {
        PageResult<MemberGiftRegistryDO> pageResult = giftRegistryService.getGiftRegistryPage(pageReqVO);
        return success(new PageResult<>(convertList(pageResult.getList(), registry -> convert(registry, null)), pageResult.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "Get gift registry")
    @Parameter(name = "id", description = "Gift registry id", required = true)
    @PreAuthorize("@ss.hasPermission('member:gift-registry:query')")
    public CommonResult<AppGiftRegistryRespVO> getGiftRegistry(@RequestParam("id") Long id) {
        MemberGiftRegistryDO registry = giftRegistryService.getGiftRegistry(id);
        return success(convert(registry, registry == null ? Collections.emptyList() : giftRegistryService.getGiftRegistryItems(id)));
    }

    @PutMapping("/status")
    @Operation(summary = "Update gift registry status")
    @PreAuthorize("@ss.hasPermission('member:gift-registry:update')")
    public CommonResult<Boolean> updateGiftRegistryStatus(@RequestBody @Valid MemberGiftRegistryStatusUpdateReqVO reqVO) {
        giftRegistryService.updateGiftRegistryStatus(reqVO);
        return success(true);
    }

    private AppGiftRegistryRespVO convert(MemberGiftRegistryDO registry, List<MemberGiftRegistryItemDO> items) {
        if (registry == null) {
            return null;
        }
        AppGiftRegistryRespVO respVO = new AppGiftRegistryRespVO();
        respVO.setId(registry.getId());
        respVO.setUserId(registry.getUserId());
        respVO.setPublicCode(registry.getPublicCode());
        respVO.setRegistrantName(registry.getRegistrantName());
        respVO.setCoRegistrantName(registry.getCoRegistrantName());
        respVO.setEmail(registry.getEmail());
        respVO.setPhone(registry.getPhone());
        respVO.setEventType(registry.getEventType());
        respVO.setEventDate(registry.getEventDate());
        respVO.setEventLocation(registry.getEventLocation());
        respVO.setVisibility(registry.getVisibility());
        respVO.setStatus(registry.getStatus());
        respVO.setGiftCardPreference(registry.getGiftCardPreference());
        respVO.setMessagePreference(registry.getMessagePreference());
        respVO.setBeforeEventAddressLine1(registry.getBeforeEventAddressLine1());
        respVO.setBeforeEventAddressLine2(registry.getBeforeEventAddressLine2());
        respVO.setBeforeEventCity(registry.getBeforeEventCity());
        respVO.setBeforeEventRegion(registry.getBeforeEventRegion());
        respVO.setBeforeEventPostalCode(registry.getBeforeEventPostalCode());
        respVO.setBeforeEventCountry(registry.getBeforeEventCountry());
        respVO.setAfterEventAddressLine1(registry.getAfterEventAddressLine1());
        respVO.setAfterEventAddressLine2(registry.getAfterEventAddressLine2());
        respVO.setAfterEventCity(registry.getAfterEventCity());
        respVO.setAfterEventRegion(registry.getAfterEventRegion());
        respVO.setAfterEventPostalCode(registry.getAfterEventPostalCode());
        respVO.setAfterEventCountry(registry.getAfterEventCountry());
        respVO.setCreateTime(registry.getCreateTime());
        respVO.setItems(items == null ? Collections.emptyList() : convertList(items, this::convert));
        return respVO;
    }

    private AppGiftRegistryItemRespVO convert(MemberGiftRegistryItemDO item) {
        AppGiftRegistryItemRespVO respVO = new AppGiftRegistryItemRespVO();
        respVO.setId(item.getId());
        respVO.setRegistryId(item.getRegistryId());
        respVO.setSpuId(item.getSpuId());
        respVO.setSkuId(item.getSkuId());
        respVO.setProductName(item.getProductName());
        respVO.setPicUrl(item.getPicUrl());
        respVO.setPrice(item.getPrice());
        respVO.setQuantityRequested(item.getQuantityRequested());
        respVO.setQuantityPurchased(item.getQuantityPurchased());
        respVO.setPriority(item.getPriority());
        respVO.setNote(item.getNote());
        return respVO;
    }

}
