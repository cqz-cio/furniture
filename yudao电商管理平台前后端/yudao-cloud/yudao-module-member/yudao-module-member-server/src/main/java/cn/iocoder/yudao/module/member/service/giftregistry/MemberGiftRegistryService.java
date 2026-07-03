package cn.iocoder.yudao.module.member.service.giftregistry;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.giftregistry.dto.MemberGiftRegistryPurchaseRecordReqDTO;
import cn.iocoder.yudao.module.member.controller.admin.giftregistry.vo.MemberGiftRegistryPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.giftregistry.vo.MemberGiftRegistryStatusUpdateReqVO;
import cn.iocoder.yudao.module.member.controller.app.giftregistry.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryDO;
import cn.iocoder.yudao.module.member.dal.dataobject.giftregistry.MemberGiftRegistryItemDO;

import java.util.List;

public interface MemberGiftRegistryService {

    String VISIBILITY_PUBLIC = "public";
    String VISIBILITY_SEARCHABLE_BY_EMAIL = "searchable_by_email";
    String VISIBILITY_INVITE_ONLY = "invite_only";
    String STATUS_ACTIVE = "active";
    String STATUS_HIDDEN = "hidden";
    String STATUS_CLOSED = "closed";
    String PRIORITY_NORMAL = "normal";

    MemberGiftRegistryDO createGiftRegistry(Long userId, AppGiftRegistryCreateReqVO reqVO);

    MemberGiftRegistryDO getMyGiftRegistry(Long userId);

    MemberGiftRegistryDO updateGiftRegistry(Long userId, AppGiftRegistryUpdateReqVO reqVO);

    MemberGiftRegistryItemDO addGiftRegistryItem(Long userId, AppGiftRegistryItemAddReqVO reqVO);

    MemberGiftRegistryItemDO updateGiftRegistryItem(Long userId, AppGiftRegistryItemUpdateReqVO reqVO);

    void deleteGiftRegistryItem(Long userId, Long id);

    MemberGiftRegistryDO getPublicGiftRegistry(String publicCode);

    PageResult<MemberGiftRegistryDO> searchPublicGiftRegistryPage(AppGiftRegistrySearchReqVO reqVO);

    MemberGiftRegistryDO getGiftRegistry(Long id);

    PageResult<MemberGiftRegistryDO> getGiftRegistryPage(MemberGiftRegistryPageReqVO reqVO);

    void updateGiftRegistryStatus(MemberGiftRegistryStatusUpdateReqVO reqVO);

    List<MemberGiftRegistryItemDO> getGiftRegistryItems(Long registryId);

    void recordPurchasedItems(List<MemberGiftRegistryPurchaseRecordReqDTO.Item> items);

}
