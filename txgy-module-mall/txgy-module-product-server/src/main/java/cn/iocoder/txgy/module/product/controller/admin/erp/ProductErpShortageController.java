package cn.iocoder.txgy.module.product.controller.admin.erp;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 缺货采购的最小 ERP 适配接口。
 *
 * 当前先实现可联调的“提交、接收、备货完成”状态流，ERP 内部采购过程不在商城实现。
 * 后续接入真实 ERP 时，只需替换 submit 方法中的接收逻辑，保留请求和状态字段即可。
 */
@Tag(name = "管理后台 - 缺货采购 ERP")
@RestController
@RequestMapping("/product/erp/shortage")
@Validated
public class ProductErpShortageController {

    private final Map<String, ShortageDemandRespVO> demands = new ConcurrentHashMap<>();

    @PostMapping("/submit")
    @Operation(summary = "提交缺货采购需求")
    public CommonResult<ShortageDemandRespVO> submit(@Valid @RequestBody ShortageDemandReqVO req) {
        ShortageDemandRespVO existing = demands.get(req.getRequestId());
        if (existing != null) {
            return success(existing); // 相同 requestId 重试不重复创建
        }
        ShortageDemandRespVO result = new ShortageDemandRespVO();
        result.setRequestId(req.getRequestId());
        result.setMallDemandNo(req.getMallDemandNo());
        result.setOrderNo(req.getOrderNo());
        result.setSkuId(req.getSkuId());
        result.setShortageQuantity(req.getShortageQuantity());
        result.setAccepted(true);
        result.setErpRequestNo("ERP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        result.setStatus("ACCEPTED");
        result.setMessage("ERP 已接收，商城进入备货中");
        result.setCreatedTime(LocalDateTime.now());
        demands.put(req.getRequestId(), result);
        return success(result);
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "查询缺货采购状态")
    public CommonResult<ShortageDemandRespVO> get(@PathVariable String requestId) {
        return success(demands.get(requestId));
    }

    @PutMapping("/{requestId}/ready")
    @Operation(summary = "模拟 ERP/WMS 库存就绪")
    public CommonResult<ShortageDemandRespVO> ready(@PathVariable String requestId) {
        ShortageDemandRespVO result = demands.get(requestId);
        if (result != null) {
            result.setStatus("INVENTORY_READY");
            result.setMessage("库存已就绪，可进入发货流程");
        }
        return success(result);
    }

    @Data
    public static class ShortageDemandReqVO {
        @NotBlank(message = "requestId 不能为空")
        private String requestId;
        @NotBlank(message = "商城采购需求单号不能为空")
        private String mallDemandNo;
        private String orderNo;
        @NotNull(message = "SKU 不能为空")
        private Long skuId;
        @NotNull(message = "缺货数量不能为空")
        private Integer shortageQuantity;
    }

    @Data
    public static class ShortageDemandRespVO {
        private String requestId;
        private String mallDemandNo;
        private String orderNo;
        private Long skuId;
        private Integer shortageQuantity;
        private Boolean accepted;
        private String erpRequestNo;
        private String status;
        private String message;
        private LocalDateTime createdTime;
    }
}
