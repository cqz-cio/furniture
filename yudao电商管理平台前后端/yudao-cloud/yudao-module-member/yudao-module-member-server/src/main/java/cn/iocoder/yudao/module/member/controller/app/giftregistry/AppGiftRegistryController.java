package cn.iocoder.yudao.module.member.controller.app.giftregistry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.giftregistry.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryDO;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryItemDO;
import cn.iocoder.yudao.module.member.service.giftregistry.MemberGiftRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "User App - Gift Registry")
@RestController
@RequestMapping("/member/gift-registry")
@Validated
public class AppGiftRegistryController {

    @Resource
    private MemberGiftRegistryService giftRegistryService;

    @PostMapping("/create")
    @Operation(summary = "Create gift registry")
    public CommonResult<AppGiftRegistryRespVO> createGiftRegistry(@RequestBody @Valid AppGiftRegistryCreateReqVO reqVO) {
        MemberGiftRegistryDO registry = giftRegistryService.createGiftRegistry(getLoginUserId(), reqVO);
        return success(convert(registry, giftRegistryService.getGiftRegistryItems(registry.getId())));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user's gift registry")
    public CommonResult<AppGiftRegistryRespVO> getMyGiftRegistry() {
        MemberGiftRegistryDO registry = giftRegistryService.getMyGiftRegistry(getLoginUserId());
        return success(convertWithItems(registry));
    }

    @PutMapping("/update")
    @Operation(summary = "Update current user's gift registry")
    public CommonResult<AppGiftRegistryRespVO> updateGiftRegistry(@RequestBody @Valid AppGiftRegistryUpdateReqVO reqVO) {
        MemberGiftRegistryDO registry = giftRegistryService.updateGiftRegistry(getLoginUserId(), reqVO);
        return success(convertWithItems(registry));
    }

    @PostMapping("/item/add")
    @Operation(summary = "Add gift registry item")
    public CommonResult<AppGiftRegistryItemRespVO> addGiftRegistryItem(@RequestBody @Valid AppGiftRegistryItemAddReqVO reqVO) {
        return success(convert(giftRegistryService.addGiftRegistryItem(getLoginUserId(), reqVO)));
    }

    @PutMapping("/item/update")
    @Operation(summary = "Update gift registry item")
    public CommonResult<AppGiftRegistryItemRespVO> updateGiftRegistryItem(@RequestBody @Valid AppGiftRegistryItemUpdateReqVO reqVO) {
        return success(convert(giftRegistryService.updateGiftRegistryItem(getLoginUserId(), reqVO)));
    }

    @DeleteMapping("/item/delete")
    @Operation(summary = "Delete gift registry item")
    public CommonResult<Boolean> deleteGiftRegistryItem(@RequestParam("id") Long id) {
        giftRegistryService.deleteGiftRegistryItem(getLoginUserId(), id);
        return success(true);
    }

    @GetMapping("/public/{publicCode}")
    @Operation(summary = "Get public gift registry")
    public CommonResult<AppGiftRegistryRespVO> getPublicGiftRegistry(@PathVariable("publicCode") String publicCode) {
        MemberGiftRegistryDO registry = giftRegistryService.getPublicGiftRegistry(publicCode);
        return success(convertWithItems(registry));
    }

    @GetMapping("/search")
    @Operation(summary = "Search public gift registries")
    public CommonResult<PageResult<AppGiftRegistryRespVO>> searchPublicGiftRegistries(@Valid AppGiftRegistrySearchReqVO reqVO) {
        PageResult<MemberGiftRegistryDO> pageResult = giftRegistryService.searchPublicGiftRegistryPage(reqVO);
        return success(new PageResult<>(convertList(pageResult.getList(), registry -> convert(registry, null)), pageResult.getTotal()));
    }

    private AppGiftRegistryRespVO convertWithItems(MemberGiftRegistryDO registry) {
        if (registry == null) {
            return null;
        }
        return convert(registry, giftRegistryService.getGiftRegistryItems(registry.getId()));
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
        respVO.setItems(items == null ? java.util.Collections.emptyList() : convertList(items, this::convert));
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
