package cn.iocoder.txgy.module.wms.controller.admin.inventory.vo.history;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.iocoder.txgy.framework.excel.core.annotations.DictFormat;
import cn.iocoder.txgy.framework.excel.core.convert.DictConvert;
import cn.iocoder.txgy.module.wms.enums.DictTypeConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - WMS 库存流水 Response VO")
@Data
@ExcelIgnoreUnannotated
public class WmsInventoryHistoryRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "商品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long itemId;
    @Schema(description = "商品编码", example = "ITEM001")
    @ExcelProperty("商品编号")
    private String itemCode;
    @Schema(description = "商品名称", example = "红富士苹果")
    @ExcelProperty("商品名称")
    private String itemName;
    @Schema(description = "商品单位", example = "箱")
    @ExcelProperty("计量单位")
    private String unit;

    @Schema(description = "商品 SKU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "4096")
    private Long skuId;
    @Schema(description = "规格编号", example = "SKU001")
    @ExcelProperty("规格编号")
    private String skuCode;
    @Schema(description = "规格名称", example = "10kg 箱装")
    @ExcelProperty("规格名称")
    private String skuName;

    @Schema(description = "仓库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "8192")
    private Long warehouseId;
    @Schema(description = "仓库名称", example = "成品仓")
    @ExcelProperty("仓库")
    private String warehouseName;

    @Schema(description = "库存变化数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10.00")
    @ExcelProperty("变化数量")
    private BigDecimal quantity;
    @Schema(description = "变化前库存数量", example = "90.00")
    @ExcelProperty("操作前")
    private BigDecimal beforeQuantity;
    @Schema(description = "变化后库存数量", example = "100.00")
    @ExcelProperty("操作后")
    private BigDecimal afterQuantity;
    @Schema(description = "单价", example = "1000.00")
    @ExcelProperty("单价(元)")
    private BigDecimal price;
    @Schema(description = "库存变化金额", example = "10000.00")
    @ExcelProperty("金额变化(元)")
    private BigDecimal totalPrice;
    @Schema(description = "备注", example = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "单据编号", example = "1024")
    private Long orderId;
    @Schema(description = "单据号", example = "RK202605110001")
    @ExcelProperty("单据号")
    private String orderNo;
    @Schema(description = "单据类型", example = "1")
    @ExcelProperty(value = "单据类型", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.ORDER_TYPE)
    private Integer orderType;
    @Schema(description = "操作人", example = "1")
    private String creator;
    @Schema(description = "操作人名称", example = "张三")
    @ExcelProperty("操作人")
    private String creatorName;
    @Schema(description = "操作时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("操作时间")
    private LocalDateTime createTime;

}
