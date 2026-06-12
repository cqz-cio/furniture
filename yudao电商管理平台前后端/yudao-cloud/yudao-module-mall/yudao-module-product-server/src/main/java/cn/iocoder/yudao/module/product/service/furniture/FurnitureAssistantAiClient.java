package cn.iocoder.yudao.module.product.service.furniture;

public interface FurnitureAssistantAiClient {

    boolean isEnabled();

    String generateAnswer(FurnitureAssistantAiRequest request);

    String getSourceName();

}
