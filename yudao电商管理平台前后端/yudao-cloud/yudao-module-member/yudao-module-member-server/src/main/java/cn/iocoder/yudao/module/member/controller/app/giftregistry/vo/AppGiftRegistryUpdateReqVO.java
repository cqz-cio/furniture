package cn.iocoder.yudao.module.member.controller.app.giftregistry.vo;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class AppGiftRegistryUpdateReqVO {

    @NotNull
    private Long id;
    private String registrantName;
    private String coRegistrantName;
    private String email;
    private String phone;
    private String eventType;
    private LocalDate eventDate;
    private String eventLocation;
    private String visibility;
    private String status;
    private Boolean giftCardPreference;
    private Boolean messagePreference;
    private String beforeEventAddressLine1;
    private String beforeEventAddressLine2;
    private String beforeEventCity;
    private String beforeEventRegion;
    private String beforeEventPostalCode;
    private String beforeEventCountry;
    private String afterEventAddressLine1;
    private String afterEventAddressLine2;
    private String afterEventCity;
    private String afterEventRegion;
    private String afterEventPostalCode;
    private String afterEventCountry;

}
