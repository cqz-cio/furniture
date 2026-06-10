package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;

public interface FurnitureAssistantService {

    FurnitureAssistantChatRespVO chat(FurnitureAssistantChatReqVO reqVO);

}
