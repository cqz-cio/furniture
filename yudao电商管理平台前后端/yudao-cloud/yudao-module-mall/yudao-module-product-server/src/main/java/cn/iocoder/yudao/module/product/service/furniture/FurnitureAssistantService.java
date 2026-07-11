package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantConversationRespVO;

public interface FurnitureAssistantService {

    FurnitureAssistantChatRespVO chat(FurnitureAssistantChatReqVO reqVO);

    FurnitureAssistantConversationRespVO getConversation(String conversationId);

    void deleteConversation(String conversationId);

}
