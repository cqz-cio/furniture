package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import lombok.Value;

import java.util.List;

@Value
public class FurnitureAssistantAiRequest {

    String message;
    String fallbackAnswer;
    List<FurnitureAssistantChatRespVO.Product> products;
    List<FurnitureAssistantKnowledgeMatch> knowledgeMatches;

}
