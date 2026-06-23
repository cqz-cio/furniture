package cn.iocoder.yudao.module.member.service.address;

import java.util.Map;

public interface GoogleAddressValidationClient {

    Map<String, Object> validate(String endpoint, String apiKey, Map<String, Object> request);

}
