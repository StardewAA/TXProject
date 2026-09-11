package cn.iocoder.txgy.module.wms.dal.dataobject.md.item;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * WMS 商品 DO
 *
 * @author OPENLAB BS
 */
@TableName("wms_item")
@KeySequence("wms_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WmsItemDO extends BaseDO {

    /**
     * 主键编号
     */
    @TableId
    private Long id;
    /**
     * 商品编号
     */
    private String code;
    /** RAW_MATERIAL / FINISHED_GOODS */
    private String materialType;
    /** 是否需要质检 */
    private Boolean qualityRequired;
    /** ERP 主数据版本 */
    private Long sourceVersion;
    /** 启用状态 */
    private Integer status;
    /**
     * 商品名称
     */
    private String name;
    /**
     * 单位
     */
    private String unit;
    /**
     * 商品分类编号
     *
     * 关联 {@link WmsItemCategoryDO#getId()}
     */
    private Long categoryId;
    /**
     * 商品品牌编号
     *
     * 关联 {@link WmsItemBrandDO#getId()}
     */
    private Long brandId;
    /**
     * 备注
     */
    private String remark;
    /** 当前有效质检报告编号 */
    private Long currentQualityReportId;


    // ========== 批次 / 保质期 / 序列号 ==========
    /**
     * 是否启用批次管理
     */
    private Boolean batchManage;
    /**
     * 是否启用保质期管理
     */
    private Boolean shelfLifeManage;
    /**
     * 是否启用序列号管理
     */
    private Boolean serialNumberManage;
    /**
     * 保质期（月）
     */
    private Integer shelfLifeMonths;
    /**
     * 生产日期
     */
    private LocalDate productionDate;
    /**
     * 有效期至
     */
    private LocalDate expirationDate;
    /**
     * 批次号规则，如「供应商编号+生产日期」
     */
    private String batchNumberRule;
    /**
     * 有效期计算规则，如「生产日期 + 24个月」
     */
    private String expirationRule;
    /**
     * 到期预警天数
     */
    private Integer expiryWarningDays;
    /**
     * 过期后是否禁止出库
     */
    private Boolean expiredOutboundBlocked;

    // ========== 包装 ==========
    /**
     * 包装规格
     */
    private String packageSpec;
    /**
     * 箱规
     */
    private String boxSpec;
    /**
     * 托盘规格
     */
    private String palletSpec;
    /**
     * 最小包装单位
     */
    private String minPackageUnit;

    // ========== 存储 ==========
    /**
     * 存储条件
     */
    private String storageCondition;
    /**
     * 温度要求
     */
    private String temperatureRequirement;
    /**
     * 是否危险品
     */
    private Boolean dangerousGoods;

}
