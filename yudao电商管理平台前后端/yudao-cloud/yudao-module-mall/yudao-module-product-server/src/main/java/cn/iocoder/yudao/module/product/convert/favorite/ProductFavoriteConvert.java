package cn.iocoder.yudao.module.product.convert.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.module.product.controller.admin.favorite.vo.ProductFavoriteRespVO;
import cn.iocoder.yudao.module.product.controller.app.favorite.vo.AppFavoriteRespVO;
import cn.iocoder.yudao.module.product.controller.app.favorite.vo.AppFavoriteReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.favorite.ProductFavoriteDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;

@Mapper
public interface ProductFavoriteConvert {

    ProductFavoriteConvert INSTANCE = Mappers.getMapper(ProductFavoriteConvert.class);

    default ProductFavoriteDO convert(Long userId, AppFavoriteReqVO reqVO) {
        return ProductFavoriteDO.builder()
                .userId(userId)
                .spuId(reqVO.getSpuId())
                .skuId(reqVO.getSkuId())
                .count(reqVO.getCount() == null || reqVO.getCount() < 1 ? 1 : reqVO.getCount())
                .spuName(reqVO.getSpuName())
                .picUrl(reqVO.getPicUrl())
                .price(reqVO.getPrice())
                .marketPrice(reqVO.getMarketPrice())
                .color(reqVO.getColor())
                .fabric(reqVO.getFabric())
                .width(reqVO.getWidth())
                .delivery(reqVO.getDelivery())
                .dimensions(reqVO.getDimensions())
                .build();
    }

    ProductFavoriteDO convert(Long userId, Long spuId);

    @Mapping(target = "id", source = "favorite.id")
    @Mapping(target = "spuName", source = "spu.name")
    default AppFavoriteRespVO convert(ProductSpuDO spu, ProductFavoriteDO favorite) {
        AppFavoriteRespVO respVO = new AppFavoriteRespVO();
        respVO.setId(favorite.getId());
        respVO.setSpuId(favorite.getSpuId());
        respVO.setSkuId(favorite.getSkuId());
        respVO.setCount(favorite.getCount() == null || favorite.getCount() < 1 ? 1 : favorite.getCount());
        respVO.setSpuName(firstNonBlank(favorite.getSpuName(), spu == null ? null : spu.getName()));
        respVO.setPicUrl(firstNonBlank(favorite.getPicUrl(), spu == null ? null : spu.getPicUrl()));
        respVO.setPrice(favorite.getPrice() == null && spu != null ? spu.getPrice() : favorite.getPrice());
        respVO.setMarketPrice(favorite.getMarketPrice() == null && spu != null ? spu.getMarketPrice() : favorite.getMarketPrice());
        respVO.setColor(favorite.getColor());
        respVO.setFabric(favorite.getFabric());
        respVO.setWidth(favorite.getWidth());
        respVO.setDelivery(favorite.getDelivery());
        respVO.setDimensions(favorite.getDimensions());
        return respVO;
    }

    default String firstNonBlank(String first, String fallback) {
        return first == null || first.trim().isEmpty() ? fallback : first;
    }

    default List<AppFavoriteRespVO> convertList(List<ProductFavoriteDO> favorites, List<ProductSpuDO> spus) {
        List<AppFavoriteRespVO> resultList = new ArrayList<>(favorites.size());
        Map<Long, ProductSpuDO> spuMap = convertMap(spus, ProductSpuDO::getId);
        for (ProductFavoriteDO favorite : favorites) {
            ProductSpuDO spuDO = spuMap.get(favorite.getSpuId());
            resultList.add(convert(spuDO, favorite));
        }
        return resultList;
    }

    default PageResult<ProductFavoriteRespVO> convertPage(PageResult<ProductFavoriteDO> pageResult, List<ProductSpuDO> spuList) {
        Map<Long, ProductSpuDO> spuMap = convertMap(spuList, ProductSpuDO::getId);
        List<ProductFavoriteRespVO> voList = CollectionUtils.convertList(pageResult.getList(), favorite -> {
            ProductSpuDO spu = spuMap.get(favorite.getSpuId());
            return convert02(spu, favorite);
        });
        return new PageResult<>(voList, pageResult.getTotal());
    }
    @Mapping(target = "id", source = "favorite.id")
    @Mapping(target = "userId", source = "favorite.userId")
    @Mapping(target = "spuId", source = "favorite.spuId")
    @Mapping(target = "skuId", source = "favorite.skuId")
    @Mapping(target = "count", source = "favorite.count")
    @Mapping(target = "color", source = "favorite.color")
    @Mapping(target = "fabric", source = "favorite.fabric")
    @Mapping(target = "width", source = "favorite.width")
    @Mapping(target = "delivery", source = "favorite.delivery")
    @Mapping(target = "dimensions", source = "favorite.dimensions")
    @Mapping(target = "picUrl", source = "spu.picUrl")
    @Mapping(target = "price", source = "spu.price")
    @Mapping(target = "marketPrice", source = "spu.marketPrice")
    @Mapping(target = "createTime", source = "favorite.createTime")
    ProductFavoriteRespVO convert02(ProductSpuDO spu, ProductFavoriteDO favorite);

}
