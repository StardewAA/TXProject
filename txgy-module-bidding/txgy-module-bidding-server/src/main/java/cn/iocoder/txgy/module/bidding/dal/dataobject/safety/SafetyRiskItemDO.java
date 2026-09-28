package cn.iocoder.txgy.module.bidding.dal.dataobject.safety;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 安许风险条目工单。表：safety_risk_item（DDL sql/history/2026-09-28-safety-risk-item.sql）。
 *
 * <p>规范出处：五页改造缺接口契约 Q18/Q19（2026-09-28）——风险条目：等级/描述/关联证照/影响主体/
 * 责任人/状态/指派；Q20 口径：重新扫描复用 /risk/recheck，evaluateOne 按档位自动生成/点亮工单。
 * <p>设计遵循/针对：assignee 为责任人；工单状态变更/处理走乐观锁 version CAS；
 * HANDLED 为终态（不复活），风险复现时扫描生成新工单。
 */
@TableName("safety_risk_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class SafetyRiskItemDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 影响企业主体 id（enterprise 主体档案） */
    private Long entId;
    /** 关联安许证 id（空=非安许来源） */
    private Long permitId;
    /** 风险等级 code：LOW/MEDIUM/HIGH/MAJOR（字典 risk_level） */
    private String riskLevel;
    /** 风险描述 */
    private String riskDesc;
    /** 关联证照/证书名称快照 */
    private String certRef;
    /** 责任人 id（指派后回填） */
    private Long assigneeId;
    /** 责任人姓名快照 */
    private String assigneeName;
    /** 工单状态 code：PENDING/ASSIGNED/HANDLING/HANDLED/IGNORED（字典 risk_item_status；终态 HANDLED/IGNORED） */
    private String status;
    /** 发现时间（=扫描落库时刻） */
    private LocalDateTime foundAt;
    /** 处理完成时间 */
    private LocalDateTime handledAt;
    /** 处理/指派备注 */
    private String handleRemark;
    /** 乐观锁版本 */
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
