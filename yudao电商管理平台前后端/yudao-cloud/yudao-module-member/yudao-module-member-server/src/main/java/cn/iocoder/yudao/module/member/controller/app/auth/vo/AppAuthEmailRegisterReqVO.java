package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "User APP - Email register Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAuthEmailRegisterReqVO {

    @Schema(description = "First name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Black")
    @NotEmpty(message = "First name cannot be empty")
    @Length(max = 30, message = "First name cannot exceed 30 characters")
    private String firstName;

    @Schema(description = "Last name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Furniture")
    @NotEmpty(message = "Last name cannot be empty")
    @Length(max = 30, message = "Last name cannot exceed 30 characters")
    private String lastName;

    @Schema(description = "Email", requiredMode = Schema.RequiredMode.REQUIRED, example = "designer@example.com")
    @NotEmpty(message = "Email cannot be empty")
    @Email(message = "Email format is incorrect")
    @Length(max = 255, message = "Email cannot exceed 255 characters")
    private String email;

    @Schema(description = "Password", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin123")
    @NotEmpty(message = "Password cannot be empty")
    @Length(min = 4, max = 16, message = "Password length must be 4-16 characters")
    private String password;

    @Schema(description = "Email verification code", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotBlank(message = "Verification code cannot be empty")
    @Length(min = 6, max = 6, message = "Verification code must be 6 characters")
    private String code;

    @Schema(description = "Email opt-in", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean emailOptIn;

    @Schema(description = "Privacy accepted", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "Please read and accept the privacy notice")
    @AssertTrue(message = "Please read and accept the privacy notice")
    private Boolean privacyAccepted;

}
