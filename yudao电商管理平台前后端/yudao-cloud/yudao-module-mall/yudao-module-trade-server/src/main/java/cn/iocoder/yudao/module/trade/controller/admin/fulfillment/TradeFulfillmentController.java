package cn.iocoder.yudao.module.trade.controller.admin.fulfillment;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.*;
import cn.iocoder.yudao.module.trade.service.fulfillment.*;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 北美履约")
@RestController
@RequestMapping("/trade/fulfillment/shipments")
@Validated
public class TradeFulfillmentController {

    @Resource
    private FulfillmentCommandService commandService;
    @Resource
    private FulfillmentTrackingService trackingService;
    @Resource
    private FulfillmentQueryService queryService;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:create')")
    @ApiAccessLog(requestEnable = false)
    public CommonResult<Long> createShipment(
            @RequestHeader(name = "Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ShipmentCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(commandService.createShipment(idempotencyKey, toCreateCommand(tenantId, reqVO)));
    }

    @PutMapping("/{id}/ready")
    @PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:update')")
    @ApiAccessLog(requestEnable = false)
    public CommonResult<Boolean> markReady(
            @RequestHeader(name = "Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @PathVariable("id") Long id, @Valid @RequestBody ShipmentVersionReqVO reqVO) {
        commandService.markReady(idempotencyKey, TenantContextHolder.getRequiredTenantId(),
                id, reqVO.getExpectedVersion());
        return success(true);
    }

    @PostMapping("/{id}/packages")
    @PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:update')")
    @ApiAccessLog(requestEnable = false)
    public CommonResult<Long> addPackage(
            @RequestHeader(name = "Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @PathVariable("id") Long id, @Valid @RequestBody ShipmentPackageCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        UpsertPackageCommand command = new UpsertPackageCommand()
                .setExpectedVersion(reqVO.getExpectedVersion())
                .setPackageNo(reqVO.getPackageNo())
                .setPackageType(reqVO.getPackageType())
                .setCarrierId(reqVO.getCarrierId())
                .setTrackingNumber(reqVO.getTrackingNumber())
                .setWeight(reqVO.getWeight())
                .setWeightUnit(reqVO.getWeightUnit())
                .setLength(reqVO.getLength())
                .setWidth(reqVO.getWidth())
                .setHeight(reqVO.getHeight())
                .setDimensionUnit(reqVO.getDimensionUnit())
                .setTenantId(tenantId)
                .setShipmentId(id);
        return success(commandService.addPackage(idempotencyKey, command));
    }

    @PostMapping("/{id}/legs")
    @PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:update')")
    @ApiAccessLog(requestEnable = false)
    public CommonResult<Long> addLeg(
            @RequestHeader(name = "Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @PathVariable("id") Long id, @Valid @RequestBody ShipmentLegCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        AddShipmentLegCommand command = new AddShipmentLegCommand()
                .setExpectedVersion(reqVO.getExpectedVersion())
                .setPackageId(reqVO.getPackageId())
                .setSequenceNo(reqVO.getSequenceNo())
                .setLegType(reqVO.getLegType())
                .setCarrierId(reqVO.getCarrierId())
                .setProviderId(reqVO.getProviderId())
                .setServiceLevel(reqVO.getServiceLevel())
                .setTrackingNumber(reqVO.getTrackingNumber())
                .setProNumber(reqVO.getProNumber())
                .setBolNumber(reqVO.getBolNumber())
                .setOriginLocation(reqVO.getOriginLocation())
                .setDestinationLocation(reqVO.getDestinationLocation())
                .setTenantId(tenantId)
                .setShipmentId(id);
        return success(commandService.addLeg(idempotencyKey, command));
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:dispatch')")
    @ApiAccessLog(requestEnable = false)
    public CommonResult<Boolean> dispatch(
            @RequestHeader(name = "Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @PathVariable("id") Long id, @Valid @RequestBody ShipmentVersionReqVO reqVO) {
        DispatchShipmentCommand command = new DispatchShipmentCommand()
                .setTenantId(TenantContextHolder.getRequiredTenantId())
                .setShipmentId(id)
                .setExpectedVersion(reqVO.getExpectedVersion());
        commandService.dispatch(idempotencyKey, command);
        return success(true);
    }

    @PostMapping("/{id}/manual-event")
    @PreAuthorize("@ss.hasPermission('trade:fulfillment:tracking:manual')")
    @ApiAccessLog(requestEnable = false)
    public CommonResult<TrackingApplyRespVO> applyManualEvent(
            @RequestHeader(name = "Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @PathVariable("id") Long id, @Valid @RequestBody ManualTrackingEventReqVO reqVO) {
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        if (operatorId == null) {
            throw new IllegalStateException("login user is required");
        }
        ApplyManualTrackingEventCommand command = new ApplyManualTrackingEventCommand()
                .setPackageId(reqVO.getPackageId())
                .setShipmentLegId(reqVO.getShipmentLegId())
                .setRequestedStatus(reqVO.getRequestedStatus())
                .setOccurredAt(reqVO.getOccurredAt())
                .setExpectedShipmentVersion(reqVO.getExpectedVersion())
                .setReason(reqVO.getReason())
                .setTenantId(TenantContextHolder.getRequiredTenantId())
                .setShipmentId(id)
                .setOperatorId(operatorId)
                .setRequestTraceId(currentTraceId());
        TrackingApplyResult result = trackingService.applyManualEvent(idempotencyKey, command);
        TrackingApplyRespVO response = new TrackingApplyRespVO();
        response.setInserted(result.inserted());
        response.setStateChanged(result.stateChanged());
        response.setPreviousStatus(result.previousStatus());
        response.setCurrentStatus(result.currentStatus());
        return success(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:query')")
    public CommonResult<ShipmentDetailRespVO> getShipment(@PathVariable("id") Long id) {
        return success(queryService.getShipment(TenantContextHolder.getRequiredTenantId(), id));
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:query')")
    public CommonResult<List<TrackingEventRespVO>> getTimeline(@PathVariable("id") Long id) {
        return success(queryService.getTimeline(TenantContextHolder.getRequiredTenantId(), id));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:query')")
    public CommonResult<PageResult<ShipmentPageItemRespVO>> getShipmentPage(@Valid ShipmentPageReqVO reqVO) {
        return success(queryService.getShipmentPage(TenantContextHolder.getRequiredTenantId(), reqVO));
    }

    private CreateShipmentCommand toCreateCommand(Long tenantId, ShipmentCreateReqVO reqVO) {
        return new CreateShipmentCommand()
                .setTenantId(tenantId)
                .setOrderId(reqVO.getOrderId())
                .setShipmentType(reqVO.getShipmentType())
                .setOriginCountry(reqVO.getOriginCountry())
                .setDestinationCountry(reqVO.getDestinationCountry())
                .setOriginTimezone(reqVO.getOriginTimezone())
                .setDestinationTimezone(reqVO.getDestinationTimezone())
                .setWarehouseId(reqVO.getWarehouseId())
                .setProviderId(reqVO.getProviderId())
                .setItems(reqVO.getItems().stream().map(item -> new CreateShipmentItemCommand()
                        .setOrderItemId(item.getOrderItemId())
                        .setSkuId(item.getSkuId())
                        .setQuantity(item.getQuantity())).toList());
    }

    private String currentTraceId() {
        String traceId = TracerUtils.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId.length() <= 64 ? traceId : traceId.substring(0, 64);
    }
}
