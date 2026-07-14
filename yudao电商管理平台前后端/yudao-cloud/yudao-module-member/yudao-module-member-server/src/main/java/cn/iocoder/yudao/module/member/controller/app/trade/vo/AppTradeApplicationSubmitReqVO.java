package cn.iocoder.yudao.module.member.controller.app.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "User App - Trade application submit request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppTradeApplicationSubmitReqVO {

    @NotEmpty(message = "Business name cannot be empty")
    @Length(max = 128, message = "Business name length cannot exceed 128")
    private String businessName;
    @NotEmpty(message = "Country cannot be empty")
    private String country;
    @NotEmpty(message = "Street cannot be empty")
    private String street;
    private String address2;
    @NotEmpty(message = "City cannot be empty")
    private String city;
    @NotEmpty(message = "State cannot be empty")
    private String state;
    @NotEmpty(message = "Postal code cannot be empty")
    private String postalCode;
    @NotEmpty(message = "Business description cannot be empty")
    private String businessDescription;
    private String website;
    private String portfolio;
    private String instagram;
    private String pinterest;
    private String houzz;
    private String linkedin;

    @NotEmpty(message = "Primary email cannot be empty")
    @Email(message = "Primary email format is invalid")
    @Length(max = 255, message = "Primary email length cannot exceed 255")
    private String primaryEmail;

    @Valid
    @NotEmpty(message = "Authorized users cannot be empty")
    private List<AuthorizedUser> authorizedUsers;

    @Valid
    @NotEmpty(message = "Business documents cannot be empty")
    private List<Attachment> businessDocuments;

    @Valid
    private List<Attachment> taxDocuments;

    private Boolean emailOptIn;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorizedUser {
        @NotEmpty(message = "First name cannot be empty")
        private String firstName;
        @NotEmpty(message = "Last name cannot be empty")
        private String lastName;
        private String title;
        private String phone;
        @NotEmpty(message = "Email cannot be empty")
        @Email(message = "Email format is invalid")
        private String email;
        private String confirmEmail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        @NotEmpty(message = "Attachment name cannot be empty")
        private String name;
        @NotEmpty(message = "Attachment URL cannot be empty")
        private String url;
    }

}
