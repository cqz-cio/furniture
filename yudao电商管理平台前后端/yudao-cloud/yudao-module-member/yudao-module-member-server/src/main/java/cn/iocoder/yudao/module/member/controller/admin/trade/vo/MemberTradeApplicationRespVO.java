package cn.iocoder.yudao.module.member.controller.admin.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Admin - Trade application response")
@Data
public class MemberTradeApplicationRespVO {

    private Long id;
    private String businessName;
    private String country;
    private String street;
    private String address2;
    private String city;
    private String state;
    private String postalCode;
    private String businessDescription;
    private String website;
    private String portfolio;
    private String instagram;
    private String pinterest;
    private String houzz;
    private String linkedin;
    private String primaryEmail;
    private List<AuthorizedUser> authorizedUsers;
    private List<Attachment> businessDocuments;
    private List<Attachment> taxDocuments;
    private Boolean emailOptIn;
    private Integer status;
    private String tradeId;
    private String reviewReason;
    private LocalDateTime reviewTime;
    private Long reviewerId;
    private LocalDateTime createTime;

    @Schema(description = "Authorized business user")
    @Data
    public static class AuthorizedUser {
        private String firstName;
        private String lastName;
        private String title;
        private String phone;
        private String email;
        private String confirmEmail;
    }

    @Schema(description = "Application attachment")
    @Data
    public static class Attachment {
        private String name;
        private String url;
    }

}
