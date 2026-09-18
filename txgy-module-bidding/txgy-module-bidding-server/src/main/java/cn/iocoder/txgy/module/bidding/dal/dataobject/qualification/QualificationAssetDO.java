package cn.iocoder.txgy.module.bidding.dal.dataobject.qualification;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 资产与技术装备要素。表：qualification_asset（DDL sql/2026-09-09-…sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-5：NET_ASSET 净资产（核查取审计值 stat_year）、
 * PLANT/MAJOR_EQUIP/TEST_INSTR、evidence_file_ids 佐证引用。
 */
@TableName("qualification_asset")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationAssetDO extends TenantBaseDO {

    private Long id;
    private Long entId;
    private String assetType;
    private String name;
    private String spec;
    private BigDecimal quantity;
    private BigDecimal valueAmount;
    /** 统计年度（净资产按申报前一年度审计报告） */
    private Short statYear;
    private String evidenceFileIds;
}
