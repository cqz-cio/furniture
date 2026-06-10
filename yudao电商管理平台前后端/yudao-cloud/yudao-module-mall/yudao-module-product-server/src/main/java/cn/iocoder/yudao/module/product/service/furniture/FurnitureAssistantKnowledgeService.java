package cn.iocoder.yudao.module.product.service.furniture;

import java.util.List;

public interface FurnitureAssistantKnowledgeService {

    List<FurnitureAssistantKnowledgeMatch> search(String message);

}
