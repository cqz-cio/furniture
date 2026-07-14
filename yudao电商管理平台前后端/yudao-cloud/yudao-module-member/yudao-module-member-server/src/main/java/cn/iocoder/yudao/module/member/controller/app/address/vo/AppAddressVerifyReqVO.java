package cn.iocoder.yudao.module.member.controller.app.address.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

@Schema(description = "User App - address verification request")
@Data
public class AppAddressVerifyReqVO {

    @Schema(description = "Address entered by the user", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotNull(message = "Address cannot be empty")
    private Address address;

    @Schema(description = "Frontend local verification result, used as fallback context")
    private Map<String, Object> localVerification;

    @Schema(description = "Address entered by the user")
    @Data
    public static class Address {

        @Schema(description = "First name")
        private String firstName;

        @Schema(description = "Last name")
        private String lastName;

        @Schema(description = "Street address")
        private String street;

        @Schema(description = "Apartment, suite, or floor")
        private String apartment;

        @Schema(description = "City")
        private String city;

        @Schema(description = "US state code")
        private String state;

        @Schema(description = "Postal code")
        private String postalCode;

        @Schema(description = "Phone")
        private String phone;

        @Schema(description = "Country")
        private String country;

    }

}
