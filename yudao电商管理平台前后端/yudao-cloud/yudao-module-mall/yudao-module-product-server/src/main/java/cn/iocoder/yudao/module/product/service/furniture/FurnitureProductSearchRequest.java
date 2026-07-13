package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantRequirements;
import lombok.Data;

@Data
public class FurnitureProductSearchRequest {

    private String message;
    private FurnitureAssistantRequirements requirements;
    private Integer limit;
    private boolean includeAllVariants;

    public static FurnitureProductSearchRequest from(String message, FurnitureAssistantRequirements requirements,
                                                     int limit) {
        FurnitureProductSearchRequest request = new FurnitureProductSearchRequest();
        request.setMessage(message);
        request.setRequirements(requirements);
        request.setLimit(limit);
        return request;
    }

}
