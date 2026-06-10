package cn.iocoder.yudao.module.product.controller.app.furniture;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.service.furniture.FurnitureAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
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

}
