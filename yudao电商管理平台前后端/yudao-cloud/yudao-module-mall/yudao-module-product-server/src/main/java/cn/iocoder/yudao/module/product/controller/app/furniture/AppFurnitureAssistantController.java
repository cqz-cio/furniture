package cn.iocoder.yudao.module.product.controller.app.furniture;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantConversationRespVO;
import cn.iocoder.yudao.module.product.service.furniture.FurnitureAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "User App - Furniture Assistant")
@RestController
@RequestMapping("/ai/furniture-assistant")
@Validated
public class AppFurnitureAssistantController {

    private final FurnitureAssistantService furnitureAssistantService;

    public AppFurnitureAssistantController(FurnitureAssistantService furnitureAssistantService) {
        this.furnitureAssistantService = furnitureAssistantService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Send a furniture assistant message")
    @PermitAll
    public CommonResult<FurnitureAssistantChatRespVO> chat(@Valid @RequestBody FurnitureAssistantChatReqVO reqVO) {
        return success(furnitureAssistantService.chat(reqVO));
    }

    @GetMapping("/conversations/{conversationId}")
    @PermitAll
    public CommonResult<FurnitureAssistantConversationRespVO> getConversation(@PathVariable String conversationId) {
        return success(furnitureAssistantService.getConversation(conversationId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    @PermitAll
    public CommonResult<Boolean> deleteConversation(@PathVariable String conversationId) {
        furnitureAssistantService.deleteConversation(conversationId);
        return success(true);
    }

}
