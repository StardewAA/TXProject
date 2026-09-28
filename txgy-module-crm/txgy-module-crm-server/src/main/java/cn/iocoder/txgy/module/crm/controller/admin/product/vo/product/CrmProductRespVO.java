package cn.iocoder.txgy.module.crm.controller.admin.product.vo.product;

import cn.iocoder.txgy.framework.excel.core.annotations.DictFormat;
import cn.iocoder.txgy.framework.excel.core.convert.DictConvert;
import cn.iocoder.txgy.module.crm.enums.DictTypeConstants;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.core.trans.vo.VO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CRM 产品 Response VO（数据实时取自 WMS 仓库库存）")
@Data
@ExcelIgnoreUnannotated
public class CrmProductRespVO implements VO {

    @Schema(description = "产品编号，即 WMS 规格编号", example = "20529")
    @ExcelProperty("产品编号")
    private Long id;

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "好产品")
    @ExcelProperty("产品名称")
    private String name;

    @Schema(description = "产品编码，即 WMS 规格编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "12306")
    @ExcelProperty("产品编码")
    private String no;

    @Schema(description = "单位", example = "2")
    @ExcelProperty(value = "单位", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.CRM_PRODUCT_UNIT)
    private Integer unit;

    @Schema(description = "单位名称，WMS 原始单位", example = "米")
    @ExcelProperty("单位名称")
    private String unitName;

    @Schema(description = "价格，单位：元", requiredMode = Schema.RequiredMode.REQUIRED, example = "8911")
    @ExcelProperty("价格，单位：元")
    private BigDecimal price;

    @Schema(description = "WMS 仓库库存数量，按所有仓库汇总", example = "120")
    @ExcelProperty("库存数量")
    private BigDecimal stockQuantity;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "上架")
    @ExcelProperty(value = "状态", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.CRM_PRODUCT_STATUS)
    private Integer status;

    @Schema(description = "产品描述", example = "你说的对")
    @ExcelProperty("产品描述")
    private String description;

    @Schema(description = "数据来源", example = "2")
    @ExcelProperty(value = "数据来源", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.CRM_PRODUCT_DATA_SOURCE)
    private Integer dataSource;

    @Schema(description = "WMS 商品编号", example = "100")
    @ExcelProperty("WMS 商品编号")
    private Long wmsItemId;

    @Schema(description = "WMS 规格编号", example = "101")
    @ExcelProperty("WMS 规格编号")
    private Long wmsSkuId;

    @Schema(description = "WMS 规格编码", example = "DEMO-PIPE-20")
    @ExcelProperty("WMS 规格编码")
    private String wmsSkuCode;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @ExcelProperty("更新时间")
    private LocalDateTime updateTime;

}
