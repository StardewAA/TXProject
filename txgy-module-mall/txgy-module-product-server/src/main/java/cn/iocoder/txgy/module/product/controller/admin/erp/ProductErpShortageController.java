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
@org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('product:spu:query')")
public class ProductErpShortageController {

    // 旧内存模拟已停用，不再返回虚假的 ERP 已接收或库存已就绪。

    @PostMapping("/submit")
    @Operation(summary = "提交缺货采购需求")
    public CommonResult<ShortageDemandRespVO> submit(@Valid @RequestBody ShortageDemandReqVO req) {
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "旧ERP内存模拟已停用；库存履约请查看订单详情中的ERP请求，缺货采购等待ERP正式对接");
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "查询缺货采购状态")
    public CommonResult<ShortageDemandRespVO> get(@PathVariable String requestId) {
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "旧ERP内存模拟已停用；库存履约请查看订单详情中的ERP请求，缺货采购等待ERP正式对接");
    }

    @PutMapping("/{requestId}/ready")
    @Operation(summary = "模拟 ERP/WMS 库存就绪")
    public CommonResult<ShortageDemandRespVO> ready(@PathVariable String requestId) {
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "旧ERP内存模拟已停用；库存履约请查看订单详情中的ERP请求，缺货采购等待ERP正式对接");
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
