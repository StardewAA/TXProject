package cn.iocoder.txgy.module.erp.service.stock.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 库存明细的创建 Request BO
 *
 * @author OPENLAB BS
 */
@Data
@NoArgsConstructor
public class ErpStockRecordCreateReqBO {

    /**
     * 产品编号
     */
    @NotNull(message = "产品编号不能为空")
    private Long productId;
    /**
     * 仓库编号
     */
    @NotNull(message = "仓库编号不能为空")
    private Long warehouseId;
    /**
     * 出入库数量
     *
     * 正数，表示入库；负数，表示出库
     */
    @NotNull(message = "出入库数量不能为空")
    private BigDecimal count;

    /**
     * 业务类型
     */
    @NotNull(message = "业务类型不能为空")
    private Integer bizType;
    /**
     * 业务编号
     */
    @NotNull(message = "业务编号不能为空")
    private Long bizId;
    /**
     * 业务项编号
     */
    @NotNull(message = "业务项编号不能为空")
    private Long bizItemId;
    /**
     * 业务单号
     */
    @NotNull(message = "业务单号不能为空")
    private String bizNo;

    /**
     * 来源系统
     *
     * 为空时由 Service 默认写入 "ERP"
     */
    private String sourceSystem;
    /**
     * 来源业务语义
     *
     * 可空。WMS 同步时用于携带来源系统自身的业务类型描述
     */
    private String sourceType;
    /**
     * 责任人用户编号
     *
     * 销售出库 / 销售退货请传销售员；为空时由 Service 默认取当前登录用户
     */
    private Long sourceUserId;

    /**
     * 保留原有 7 参构造，避免历史调用方（采购入库/退货、其它出入库、调拨、盘点）被迫改动。
     * 归属字段请通过 setter 单独设置。
     */
    public ErpStockRecordCreateReqBO(Long productId, Long warehouseId, BigDecimal count,
                                     Integer bizType, Long bizId, Long bizItemId, String bizNo) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.count = count;
        this.bizType = bizType;
        this.bizId = bizId;
        this.bizItemId = bizItemId;
        this.bizNo = bizNo;
    }

}
