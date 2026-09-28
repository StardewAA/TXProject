package cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.item;

import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.sku.WmsItemSkuSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.quality.WmsItemQualityReportSaveReqVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - WMS 商品创建/更新 Request VO")
@Data
public class WmsItemSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "商品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "ITEM001")
    @NotBlank(message = "商品编号不能为空")
    @Size(max = 20, message = "商品编号长度不能超过 20 个字符")
    private String code;

    @Schema(description = "商品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "华为 nova flip")
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 60, message = "商品名称长度不能超过 60 个字符")
    private String name;

    @Schema(description = "商品分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "商品分类不能为空")
    private Long categoryId;

    @Schema(description = "单位", example = "台")
    @Size(max = 20, message = "单位长度不能超过 20 个字符")
    private String unit;

    @Schema(description = "商品品牌编号", example = "1")
    private Long brandId;

    @Schema(description = "备注", example = "备注")
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;

    @Schema(description = "物料类型", example = "FINISHED_GOODS")
    @Size(max = 32, message = "物料类型长度不能超过 32 个字符")
    private String materialType;

    @Schema(description = "是否需要质检")
    private Boolean qualityRequired;

    @Schema(description = "状态", example = "0")
    private Integer status;

    // ========== 批次 / 保质期 / 序列号 ==========
    @Schema(description = "是否启用批次管理")
    private Boolean batchManage;

    @Schema(description = "是否启用保质期管理")
    private Boolean shelfLifeManage;

    @Schema(description = "是否启用序列号管理")
    private Boolean serialNumberManage;

    @Schema(description = "保质期（月）", example = "24")
    private Integer shelfLifeMonths;

    @Schema(description = "生产日期")
    private LocalDate productionDate;

    @Schema(description = "有效期至")
    private LocalDate expirationDate;

    @Schema(description = "批次号规则", example = "供应商编号+生产日期")
    @Size(max = 255, message = "批次号规则长度不能超过 255 个字符")
    private String batchNumberRule;

    @Schema(description = "有效期计算规则", example = "生产日期 + 24个月")
    @Size(max = 255, message = "有效期计算规则长度不能超过 255 个字符")
    private String expirationRule;

    @Schema(description = "到期预警天数", example = "30")
    private Integer expiryWarningDays;

    @Schema(description = "过期后是否禁止出库")
    private Boolean expiredOutboundBlocked;

    // ========== 包装 ==========
    @Schema(description = "包装规格", example = "20kg/组")
    @Size(max = 255, message = "包装规格长度不能超过 255 个字符")
    private String packageSpec;

    @Schema(description = "箱规", example = "1组/箱")
    @Size(max = 255, message = "箱规长度不能超过 255 个字符")
    private String boxSpec;

    @Schema(description = "托盘规格", example = "20组/托盘")
    @Size(max = 255, message = "托盘规格长度不能超过 255 个字符")
    private String palletSpec;

    @Schema(description = "最小包装单位", example = "组")
    @Size(max = 32, message = "最小包装单位长度不能超过 32 个字符")
    private String minPackageUnit;

    // ========== 存储 ==========
    @Schema(description = "存储条件", example = "密封避光")
    @Size(max = 500, message = "存储条件长度不能超过 500 个字符")
    private String storageCondition;

    @Schema(description = "温度要求", example = "5-35℃")
    @Size(max = 64, message = "温度要求长度不能超过 64 个字符")
    private String temperatureRequirement;

    @Schema(description = "是否危险品")
    private Boolean dangerousGoods;

    @Schema(description = "规格列表")
    @Valid
    @NotEmpty(message = "至少包含一个商品规格")
    private List<WmsItemSkuSaveReqVO> skus;

    @Schema(description = "首份质检报告，仅创建商品时生效")
    @Valid
    private WmsItemQualityReportSaveReqVO initialQualityReport;

}
