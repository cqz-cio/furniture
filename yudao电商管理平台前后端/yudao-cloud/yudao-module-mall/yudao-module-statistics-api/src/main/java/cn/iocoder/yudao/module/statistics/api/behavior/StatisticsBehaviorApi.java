package cn.iocoder.yudao.module.statistics.api.behavior;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.statistics.api.behavior.dto.CartBehaviorRecordReqDTO;
import cn.iocoder.yudao.module.statistics.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
@FeignClient(name=ApiConstants.NAME)
public interface StatisticsBehaviorApi { String PREFIX=ApiConstants.PREFIX+"/behavior"; @PostMapping(PREFIX+"/cart-added") CommonResult<Boolean> recordCartAdded(@RequestBody @Valid CartBehaviorRecordReqDTO request); }
