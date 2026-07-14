package cn.iocoder.yudao.module.product.service.furniture.conversation;

import java.util.Optional;

public interface FurnitureAssistantConversationStore {

    Optional<FurnitureAssistantConversation> find(String conversationId);

    void save(FurnitureAssistantConversation conversation);

    void delete(String conversationId);

}
