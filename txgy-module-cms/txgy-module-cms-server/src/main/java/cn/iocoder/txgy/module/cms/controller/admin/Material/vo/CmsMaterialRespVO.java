package cn.iocoder.txgy.module.cms.controller.admin.Material.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 材料参数数据库 Response VO")
@Data
public class CmsMaterialRespVO {

    @Schema(description = "主键")
    private Long materialId;
    @Schema(description = "材料名称")
    private String materialName;
    @Schema(description = "大类：1无机保温 2有机保温 3防腐涂料 4衬里材料 5防腐管件 6预制制品")
    private Integer materialCategory;
    @Schema(description = "子类")
    private String materialSubcategory;
    @Schema(description = "品牌")
    private String brand;
    @Schema(description = "生产厂家")
    private String manufacturer;
    @Schema(description = "密度 kg/m³")
    private BigDecimal density;
    @Schema(description = "导热系数 W/(m·K)")
    private BigDecimal thermalConductivity;
    @Schema(description = "最低耐温℃")
    private Integer tempMin;
    @Schema(description = "最高耐温℃")
    private Integer tempMax;
    @Schema(description = "抗压强度MPa")
    private BigDecimal compressiveStrength;
    @Schema(description = "附着力MPa")
    private BigDecimal adhesion;
    @Schema(description = "耐腐蚀性能")
    private String corrosionResistance;
    @Schema(description = "燃烧等级")
    private String combustionGrade;
    @Schema(description = "使用寿命")
    private String serviceLife;
    @Schema(description = "适用工况")
    private String applicableCondition;
    @Schema(description = "适用领域JSON数组")
    private List<String> industryField;
    @Schema(description = "防护对象JSON数组")
    private List<String> protectObject;
    @Schema(description = "施工工艺要求")
    private String constructionProcess;
    @Schema(description = "检测标准")
    private String inspectionStandard;
    @Schema(description = "检测报告附件ID")
    private Long inspectionReportId;
    @Schema(description = "优点")
    private String advantages;
    @Schema(description = "缺点")
    private String disadvantages;
    @Schema(description = "推荐场景")
    private String recommendScene;
    @Schema(description = "参考市场价")
    private BigDecimal referencePrice;
    @Schema(description = "价格单位")
    private String priceUnit;
    @Schema(description = "价格更新时间")
    private LocalDate priceUpdateTime;
    @Schema(description = "产品图片URL数组")
    private List<String> productImages;
    @Schema(description = "说明书附件ID")
    private Long productManualId;
    @Schema(description = "关联标准ID数组")
    private List<String> relatedStandardIds;
    @Schema(description = "浏览量")
    private Integer viewCount;
    @Schema(description = "收藏量")
    private Integer collectCount;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "审核备注")
    private String auditRemark;
    @Schema(description = "发布状态")
    private Integer publishStatus;
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;
    @Schema(description = "创建人")
    private String createBy;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
