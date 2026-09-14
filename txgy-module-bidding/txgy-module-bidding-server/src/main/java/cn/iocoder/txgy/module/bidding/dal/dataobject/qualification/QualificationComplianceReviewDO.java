package cn.iocoder.txgy.module.bidding.dal.dataobject.qualification;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 达标动态核查（只追加 · 防抵赖）。表：qualification_compliance_review（DDL sql/2026-09-09-…sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-6：std_version 依据可复算、element_snapshot
 * 四要素时点快照（类比自查双快照）、PASS/RISK/FAIL 三档、diff_items 明细、reviewer/reviewed_at（季度 RANGE 分区）。
 */
@TableName("qualification_compliance_review")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationComplianceReviewDO extends TenantBaseDO {

    private Long id;
    private Long entId;
    private Long qualId;
    /** 依据标准版本 */
    private String stdVersion;
    /** SELF/DYNAMIC/PRE_APPLY */
    private String reviewType;
    /** 四要素时点快照 JSON */
    private String elementSnapshot;
    /** PASS/RISK/FAIL */
    private String reviewResult;
    /** 不达标/风险项明细 JSON */
    private String diffItems;
    private Long reviewer;
    private LocalDateTime reviewedAt;
    /** 申报前核查关联申报流 */
    private Long linkedApplyId;
}
