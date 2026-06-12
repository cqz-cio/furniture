package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class FurnitureProductSearchResult {

    private List<FurnitureAssistantChatRespVO.Product> products;

    public static FurnitureProductSearchResult empty() {
        return of(Collections.emptyList());
    }

    public static FurnitureProductSearchResult of(List<FurnitureAssistantChatRespVO.Product> products) {
        FurnitureProductSearchResult result = new FurnitureProductSearchResult();
        result.setProducts(products == null ? Collections.emptyList() : products);
        return result;
    }

}
