package cn.iocoder.yudao.module.product.service.furniture.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FurnitureAssistantConversationTest {

    @Test
    void shouldKeepOnlyLatestTwelveMessages() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");

        for (int i = 0; i < 14; i++) {
            conversation.appendMessage("user", "message-" + i);
        }

        assertEquals(12, conversation.getMessages().size());
        assertEquals("message-2", conversation.getMessages().get(0).getContent());
        assertEquals("message-13", conversation.getMessages().get(11).getContent());
    }

}
