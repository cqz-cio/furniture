package cn.iocoder.yudao.module.member.service.giftregistry;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.giftregistry.dto.MemberGiftRegistryPurchaseRecordReqDTO;
import cn.iocoder.yudao.module.member.controller.admin.giftregistry.vo.MemberGiftRegistryPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.giftregistry.vo.MemberGiftRegistryStatusUpdateReqVO;
import cn.iocoder.yudao.module.member.controller.app.giftregistry.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryDO;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryItemDO;
import cn.iocoder.yudao.module.member.dal.mysql.giftregistry.MemberGiftRegistryItemMapper;
import cn.iocoder.yudao.module.member.dal.mysql.giftregistry.MemberGiftRegistryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.GIFT_REGISTRY_ACCESS_DENIED;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.GIFT_REGISTRY_NOT_EXISTS;

@Service
public class MemberGiftRegistryServiceImpl implements MemberGiftRegistryService {

    @Resource
    private MemberGiftRegistryMapper giftRegistryMapper;
    @Resource
    private MemberGiftRegistryItemMapper giftRegistryItemMapper;

    @Override
    @Transactional
    public MemberGiftRegistryDO createGiftRegistry(Long userId, AppGiftRegistryCreateReqVO reqVO) {
        MemberGiftRegistryDO registry = buildRegistry(userId, new MemberGiftRegistryDO(), reqVO);
        registry.setPublicCode(buildPublicCode(userId, reqVO));
        registry.setStatus(StrUtil.blankToDefault(reqVO.getStatus(), STATUS_ACTIVE));
        giftRegistryMapper.insert(registry);
        return registry;
    }

    @Override
    public MemberGiftRegistryDO getMyGiftRegistry(Long userId) {
        return giftRegistryMapper.selectByUserId(userId);
    }

    @Override
    @Transactional
    public MemberGiftRegistryDO updateGiftRegistry(Long userId, AppGiftRegistryUpdateReqVO reqVO) {
        MemberGiftRegistryDO existing = validateOwner(reqVO.getId(), userId);
        MemberGiftRegistryDO update = buildRegistryUpdate(userId, new MemberGiftRegistryDO().setId(reqVO.getId()), reqVO);
        update.setPublicCode(existing.getPublicCode());
        update.setStatus(StrUtil.blankToDefault(reqVO.getStatus(), existing.getStatus()));
        giftRegistryMapper.updateById(update);
        return giftRegistryMapper.selectById(reqVO.getId());
    }

    @Override
    @Transactional
    public MemberGiftRegistryItemDO addGiftRegistryItem(Long userId, AppGiftRegistryItemAddReqVO reqVO) {
        validateOwner(reqVO.getRegistryId(), userId);
        MemberGiftRegistryItemDO item = MemberGiftRegistryItemDO.builder()
                .registryId(reqVO.getRegistryId())
                .userId(userId)
                .spuId(reqVO.getSpuId())
                .skuId(reqVO.getSkuId())
                .productName(reqVO.getProductName())
                .picUrl(reqVO.getPicUrl())
                .price(reqVO.getPrice())
                .quantityRequested(reqVO.getQuantityRequested() == null ? 1 : reqVO.getQuantityRequested())
                .quantityPurchased(0)
                .priority(StrUtil.blankToDefault(reqVO.getPriority(), PRIORITY_NORMAL))
                .note(reqVO.getNote())
                .build();
        giftRegistryItemMapper.insert(item);
        return item;
    }

    @Override
    @Transactional
    public MemberGiftRegistryItemDO updateGiftRegistryItem(Long userId, AppGiftRegistryItemUpdateReqVO reqVO) {
        MemberGiftRegistryItemDO item = giftRegistryItemMapper.selectByIdAndUserId(reqVO.getId(), userId);
        if (item == null) {
            throw exception(GIFT_REGISTRY_NOT_EXISTS);
        }
        giftRegistryItemMapper.updateById(new MemberGiftRegistryItemDO()
                .setId(reqVO.getId())
                .setQuantityRequested(reqVO.getQuantityRequested())
                .setPriority(reqVO.getPriority())
                .setNote(reqVO.getNote()));
        return giftRegistryItemMapper.selectById(reqVO.getId());
    }

    @Override
    @Transactional
    public void deleteGiftRegistryItem(Long userId, Long id) {
        MemberGiftRegistryItemDO item = giftRegistryItemMapper.selectByIdAndUserId(id, userId);
        if (item == null) {
            throw exception(GIFT_REGISTRY_NOT_EXISTS);
        }
        giftRegistryItemMapper.deleteById(id);
    }

    @Override
    public MemberGiftRegistryDO getPublicGiftRegistry(String publicCode) {
        MemberGiftRegistryDO registry = giftRegistryMapper.selectPublicByPublicCode(publicCode);
        if (registry == null) {
            throw exception(GIFT_REGISTRY_NOT_EXISTS);
        }
        return registry;
    }

    @Override
    public PageResult<MemberGiftRegistryDO> searchPublicGiftRegistryPage(AppGiftRegistrySearchReqVO reqVO) {
        applyEventMonth(reqVO);
        return giftRegistryMapper.selectPublicPage(reqVO);
    }

    @Override
    public MemberGiftRegistryDO getGiftRegistry(Long id) {
        return giftRegistryMapper.selectById(id);
    }

    @Override
    public PageResult<MemberGiftRegistryDO> getGiftRegistryPage(MemberGiftRegistryPageReqVO reqVO) {
        return giftRegistryMapper.selectPage(reqVO);
    }

    @Override
    @Transactional
    public void updateGiftRegistryStatus(MemberGiftRegistryStatusUpdateReqVO reqVO) {
        MemberGiftRegistryDO registry = giftRegistryMapper.selectById(reqVO.getId());
        if (registry == null) {
            throw exception(GIFT_REGISTRY_NOT_EXISTS);
        }
        giftRegistryMapper.updateById(new MemberGiftRegistryDO()
                .setId(reqVO.getId())
                .setStatus(reqVO.getStatus()));
    }

    @Override
    public List<MemberGiftRegistryItemDO> getGiftRegistryItems(Long registryId) {
        return giftRegistryItemMapper.selectListByRegistryId(registryId);
    }

    @Override
    @Transactional
    public void recordPurchasedItems(List<MemberGiftRegistryPurchaseRecordReqDTO.Item> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (MemberGiftRegistryPurchaseRecordReqDTO.Item purchaseItem : items) {
            recordPurchasedItem(purchaseItem);
        }
    }

    private void recordPurchasedItem(MemberGiftRegistryPurchaseRecordReqDTO.Item purchaseItem) {
        if (purchaseItem == null || purchaseItem.getRegistryItemId() == null
                || purchaseItem.getCount() == null || purchaseItem.getCount() <= 0) {
            return;
        }
        MemberGiftRegistryItemDO item = giftRegistryItemMapper.selectById(purchaseItem.getRegistryItemId());
        if (item == null || !Objects.equals(item.getRegistryId(), purchaseItem.getRegistryId())) {
            return;
        }
        int current = item.getQuantityPurchased() == null ? 0 : item.getQuantityPurchased();
        int requested = item.getQuantityRequested() == null ? current + purchaseItem.getCount() : item.getQuantityRequested();
        int next = Math.min(requested, current + purchaseItem.getCount());
        if (next == current) {
            return;
        }
        giftRegistryItemMapper.updateById(new MemberGiftRegistryItemDO()
                .setId(item.getId())
                .setQuantityPurchased(next));
    }

    private MemberGiftRegistryDO validateOwner(Long registryId, Long userId) {
        MemberGiftRegistryDO registry = giftRegistryMapper.selectById(registryId);
        if (registry == null) {
            throw exception(GIFT_REGISTRY_NOT_EXISTS);
        }
        if (!userId.equals(registry.getUserId())) {
            throw exception(GIFT_REGISTRY_ACCESS_DENIED);
        }
        return registry;
    }

    private MemberGiftRegistryDO buildRegistry(Long userId, MemberGiftRegistryDO registry, AppGiftRegistryCreateReqVO reqVO) {
        registry.setUserId(userId);
        registry.setRegistrantName(reqVO.getRegistrantName());
        registry.setCoRegistrantName(reqVO.getCoRegistrantName());
        registry.setEmail(reqVO.getEmail());
        registry.setPhone(reqVO.getPhone());
        registry.setEventType(reqVO.getEventType());
        registry.setEventDate(reqVO.getEventDate());
        registry.setEventLocation(reqVO.getEventLocation());
        registry.setVisibility(StrUtil.blankToDefault(reqVO.getVisibility(), VISIBILITY_PUBLIC));
        registry.setGiftCardPreference(reqVO.getGiftCardPreference() != null && reqVO.getGiftCardPreference());
        registry.setMessagePreference(reqVO.getMessagePreference() == null || reqVO.getMessagePreference());
        registry.setBeforeEventAddressLine1(reqVO.getBeforeEventAddressLine1());
        registry.setBeforeEventAddressLine2(reqVO.getBeforeEventAddressLine2());
        registry.setBeforeEventCity(reqVO.getBeforeEventCity());
        registry.setBeforeEventRegion(reqVO.getBeforeEventRegion());
        registry.setBeforeEventPostalCode(reqVO.getBeforeEventPostalCode());
        registry.setBeforeEventCountry(StrUtil.blankToDefault(reqVO.getBeforeEventCountry(), "United States"));
        registry.setAfterEventAddressLine1(reqVO.getAfterEventAddressLine1());
        registry.setAfterEventAddressLine2(reqVO.getAfterEventAddressLine2());
        registry.setAfterEventCity(reqVO.getAfterEventCity());
        registry.setAfterEventRegion(reqVO.getAfterEventRegion());
        registry.setAfterEventPostalCode(reqVO.getAfterEventPostalCode());
        registry.setAfterEventCountry(StrUtil.blankToDefault(reqVO.getAfterEventCountry(), "United States"));
        return registry;
    }

    private MemberGiftRegistryDO buildRegistryUpdate(Long userId, MemberGiftRegistryDO registry, AppGiftRegistryUpdateReqVO reqVO) {
        registry.setUserId(userId);
        registry.setRegistrantName(reqVO.getRegistrantName());
        registry.setCoRegistrantName(reqVO.getCoRegistrantName());
        registry.setEmail(reqVO.getEmail());
        registry.setPhone(reqVO.getPhone());
        registry.setEventType(reqVO.getEventType());
        registry.setEventDate(reqVO.getEventDate());
        registry.setEventLocation(reqVO.getEventLocation());
        registry.setVisibility(reqVO.getVisibility());
        registry.setGiftCardPreference(reqVO.getGiftCardPreference());
        registry.setMessagePreference(reqVO.getMessagePreference());
        registry.setBeforeEventAddressLine1(reqVO.getBeforeEventAddressLine1());
        registry.setBeforeEventAddressLine2(reqVO.getBeforeEventAddressLine2());
        registry.setBeforeEventCity(reqVO.getBeforeEventCity());
        registry.setBeforeEventRegion(reqVO.getBeforeEventRegion());
        registry.setBeforeEventPostalCode(reqVO.getBeforeEventPostalCode());
        registry.setBeforeEventCountry(reqVO.getBeforeEventCountry());
        registry.setAfterEventAddressLine1(reqVO.getAfterEventAddressLine1());
        registry.setAfterEventAddressLine2(reqVO.getAfterEventAddressLine2());
        registry.setAfterEventCity(reqVO.getAfterEventCity());
        registry.setAfterEventRegion(reqVO.getAfterEventRegion());
        registry.setAfterEventPostalCode(reqVO.getAfterEventPostalCode());
        registry.setAfterEventCountry(reqVO.getAfterEventCountry());
        return registry;
    }

    private String buildPublicCode(Long userId, AppGiftRegistryCreateReqVO reqVO) {
        String name = StrUtil.blankToDefault(reqVO.getRegistrantName(), "registry")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String date = reqVO.getEventDate() == null ? String.valueOf(System.currentTimeMillis()) : reqVO.getEventDate().toString();
        return "registry-" + name + "-" + userId + "-" + date;
    }

    private void applyEventMonth(AppGiftRegistrySearchReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getEventMonth())) {
            return;
        }
        YearMonth month = YearMonth.parse(reqVO.getEventMonth());
        reqVO.setEventStart(month.atDay(1));
        reqVO.setEventEnd(month.atEndOfMonth());
    }

}
