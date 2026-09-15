package cn.iocoder.txgy.module.cms.dal.dataobject.Material;

import cn.iocoder.txgy.module.cms.dal.dataobject.CmsBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 材料参数数据库 DO
 *
 * @author yuting_feng
 */
@TableName(value = "cms_material", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsMaterialDO extends CmsBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long materialId;
    /**
     * 材料名称
     */
    private String materialName;
    /**
     * 大类：1无机保温 2有机保温 3防腐涂料 4衬里材料 5防腐管件 6预制制品
     */
    private Integer materialCategory;
    /**
     * 子类
     */
    private String materialSubcategory;
    /**
     * 品牌
     */
    private String brand;
    /**
     * 生产厂家
     */
    private String manufacturer;
    /**
     * 密度 kg/m³
     */
    private BigDecimal density;
    /**
     * 导热系数 W/(m·K)
     */
    private BigDecimal thermalConductivity;
    /**
     * 最低耐温℃
     */
    private Integer tempMin;
    /**
     * 最高耐温℃
     */
    private Integer tempMax;
    /**
     * 抗压强度MPa
     */
    private BigDecimal compressiveStrength;
    /**
     * 附着力MPa
     */
    private BigDecimal adhesion;
    /**
     * 耐腐蚀性能
     */
    private String corrosionResistance;
    /**
     * 燃烧等级
     */
    private String combustionGrade;
    /**
     * 使用寿命
     */
    private String serviceLife;
    /**
     * 适用工况
     */
    private String applicableCondition;
    /**
     * 适用领域JSON数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> industryField;
    /**
     * 防护对象JSON数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> protectObject;
    /**
     * 施工工艺要求
     */
    private String constructionProcess;
    /**
     * 检测标准
     */
    private String inspectionStandard;
    /**
     * 检测报告附件ID
     */
    private Long inspectionReportId;
    /**
     * 优点
     */
    private String advantages;
    /**
     * 缺点
     */
    private String disadvantages;
    /**
     * 推荐场景
     */
    private String recommendScene;
    /**
     * 参考市场价
     */
    private BigDecimal referencePrice;
    /**
     * 价格单位
     */
    private String priceUnit;
    /**
     * 价格更新时间
     */
    private LocalDate priceUpdateTime;
    /**
     * 产品图片URL数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> productImages;
    /**
     * 说明书附件ID
     */
    private Long productManualId;
    /**
     * 关联标准ID数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> relatedStandardIds;
    /**
     * 浏览量
     */
    private Integer viewCount;
    /**
     * 收藏量
     */
    private Integer collectCount;
    /**
     * 审核状态
     */
    private Integer auditStatus;
    /**
     * 审核备注
     */
    private String auditRemark;
    /**
     * 发布状态
     */
    private Integer publishStatus;
    /**
     * 发布时间
     */
    private LocalDateTime publishTime;
}
