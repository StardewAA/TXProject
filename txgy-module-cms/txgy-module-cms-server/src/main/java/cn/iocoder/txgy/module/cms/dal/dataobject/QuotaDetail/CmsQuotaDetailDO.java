package cn.iocoder.txgy.module.cms.dal.dataobject.QuotaDetail;

import cn.iocoder.txgy.module.cms.dal.dataobject.CmsBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/**
 * 定额明细表 DO
 *
 * @author yuting_feng
 */
@TableName(value = "cms_quota_detail", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsQuotaDetailDO extends CmsBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long detailId;
    /**
     * 关联主表ID
     */
    private Long quotaId;
    /**
     * 定额编号
     */
    private String quotaNo;
    /**
     * 项目名称
     */
    private String itemName;
    /**
     * 计量单位
     */
    private String unit;
    /**
     * 基价（元）
     */
    private BigDecimal basePrice;
    /**
     * 人工费
     */
    private BigDecimal laborCost;
    /**
     * 机械费
     */
    private BigDecimal machineCost;
}
