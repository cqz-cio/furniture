package cn.iocoder.yudao.module.product.service.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.favorite.vo.ProductFavoritePageReqVO;
import cn.iocoder.yudao.module.product.controller.app.favorite.vo.AppFavoritePageReqVO;
import cn.iocoder.yudao.module.product.controller.app.favorite.vo.AppFavoriteReqVO;
import cn.iocoder.yudao.module.product.convert.favorite.ProductFavoriteConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.favorite.ProductFavoriteDO;
import cn.iocoder.yudao.module.product.dal.mysql.favorite.ProductFavoriteMapper;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FAVORITE_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FAVORITE_NOT_EXISTS;

/**
 * 商品收藏 Service 实现类
 *
 * @author jason
 */
@Service
@Validated
public class ProductFavoriteServiceImpl implements ProductFavoriteService {

    @Resource
    private ProductFavoriteMapper productFavoriteMapper;

    @Override
    public Long createFavorite(Long userId, Long spuId) {
        AppFavoriteReqVO reqVO = new AppFavoriteReqVO();
        reqVO.setSpuId(spuId);
        return createFavorite(userId, reqVO);
    }

    @Override
    public Long createFavorite(Long userId, AppFavoriteReqVO reqVO) {
        ProductFavoriteDO favorite = productFavoriteMapper.selectByUserIdAndSpuIdAndSkuId(userId, reqVO.getSpuId(), reqVO.getSkuId());
        if (favorite != null) {
            throw exception(FAVORITE_EXISTS);
        }

        ProductFavoriteDO entity = ProductFavoriteConvert.INSTANCE.convert(userId, reqVO);
        productFavoriteMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void deleteFavorite(Long userId, Long spuId) {
        AppFavoriteReqVO reqVO = new AppFavoriteReqVO();
        reqVO.setSpuId(spuId);
        deleteFavorite(userId, reqVO);
    }

    @Override
    public void deleteFavorite(Long userId, AppFavoriteReqVO reqVO) {
        ProductFavoriteDO favorite = reqVO.getSkuId() != null
                ? productFavoriteMapper.selectByUserIdAndSpuIdAndSkuId(userId, reqVO.getSpuId(), reqVO.getSkuId())
                : productFavoriteMapper.selectByUserIdAndSpuId(userId, reqVO.getSpuId());
        if (favorite == null) {
            throw exception(FAVORITE_NOT_EXISTS);
        }

        productFavoriteMapper.deleteById(favorite.getId());
    }

    @Override
    public void updateFavoriteCount(Long userId, AppFavoriteReqVO reqVO) {
        ProductFavoriteDO favorite = productFavoriteMapper.selectByUserIdAndSpuIdAndSkuId(userId, reqVO.getSpuId(), reqVO.getSkuId());
        if (favorite == null) {
            throw exception(FAVORITE_NOT_EXISTS);
        }

        favorite.setCount(reqVO.getCount() == null || reqVO.getCount() < 1 ? 1 : reqVO.getCount());
        productFavoriteMapper.updateById(favorite);
    }

    @Override
    public PageResult<ProductFavoriteDO> getFavoritePage(Long userId, @Valid AppFavoritePageReqVO reqVO) {
        return productFavoriteMapper.selectPageByUserAndType(userId, reqVO);
    }

    @Override
    public PageResult<ProductFavoriteDO> getFavoritePage(@Valid ProductFavoritePageReqVO reqVO) {
        return productFavoriteMapper.selectPageByUserId(reqVO);
    }

    @Override
    public ProductFavoriteDO getFavorite(Long userId, Long spuId) {
        return productFavoriteMapper.selectByUserIdAndSpuId(userId, spuId);
    }

    @Override
    public Long getFavoriteCount(Long userId) {
        return productFavoriteMapper.selectCountByUserId(userId);
    }

    @Override
    public void deleteFavoriteBySpuId(Long spuId) {
        productFavoriteMapper.deleteBySpuId(spuId);
    }

}
