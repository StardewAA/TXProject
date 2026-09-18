package cn.iocoder.txgy.module.mes.api.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RPC 服务 - ERP 推送生产工单到 MES 的请求
 *
 * 工单宿主与生命周期归 MES：ERP 销售订单审批通过后，按行项推送创建请求，
 * MES 侧落库为 mes_pro_work_order，来源类型固定为"ERP订单"（orderSourceType=3），
 * orderSourceCode 记录 ERP 销售订单号，保证两端可追溯。
 *
 * 工单编码由 MES 侧按 PRO_WORK_ORDER_CODE 自动编码规则生成，不信任外部传入；
 * 排产、报工、完工等后续动作仍在 MES 内完成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesWorkOrderCreateReqDTO extends MesIntegrationBaseReqDTO {

    /**
     * 来源单据编号（ERP 销售订单号）
     */
    @NotBlank(message = "orderSourceCode 不能为空")
    private String orderSourceCode;

    /**
     * 产品物料编码（MES 侧 mes_md_item.code，需已通过主数据同步落库）
     */
    @NotBlank(message = "itemCode 不能为空")
    private String itemCode;

    /**
     * 产品名称，用于生成工单名称
     */
    @NotBlank(message = "productName 不能为空")
    private String productName;

    @NotNull(message = "quantity 不能为空")
    private BigDecimal quantity;

    @NotNull(message = "requestDate 不能为空")
    private LocalDateTime requestDate;

    /**
     * 客户编码（可选，MES 侧回填 clientId）
     */
    private String customerCode;

    private String remark;

}
