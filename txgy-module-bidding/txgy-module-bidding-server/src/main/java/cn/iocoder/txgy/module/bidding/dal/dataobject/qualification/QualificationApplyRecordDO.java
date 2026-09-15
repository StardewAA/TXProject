package cn.iocoder.txgy.module.bidding.dal.dataobject.qualification;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 企业资质业务申报流（T 轨 · 状态机 + 乐观锁）。表：qualification_apply_record（DDL 见 sql/2026-09-07-…sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-7：状态机 DRAFT→SUBMITTED→ACCEPTED→REVIEWING→
 * APPROVED→PUBLISHED→ISSUED / REJECTED；办结回写主档 cert_no/valid_to（同事务 + version 乐观锁）；
 * material_snapshot 四要素佐证快照（与 15-6 达标核查联动）。
 */
@TableName("qualification_apply_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationApplyRecordDO extends TenantBaseDO {

    /** 主键（雪花 ID；=设计 apply_id） */
    private Long id;
    /** 归属企业 */
    private Long entId;
    /** 目标资质（新办可空） */
    private Long qualId;
    /** 申报类型 code：NEW/UPGRADE/RENEW/ADDITION/CHANGE/CANCEL */
    private String applyType;
    /** 目标等级（升级/增项用） */
    private String targetGrade;
    /** 提交日期 */
    private LocalDate submitDate;
    /** 预计办结日期 */
    private LocalDate dueDate;
    /** 申报状态 code（状态机见 QualificationApplyStatusEnum） */
    private String status;
    /** 申报材料清单快照（JSON 字符串） */
    private String materialSnapshot;
    /** 关联达标核查记录（JSON 字符串） */
    private String reviewIds;
    /** 批复/许可决定文号 */
    private String resultNo;
    /** 办结后回写主档 cert_no */
    private String newCertNo;
    /** 办结后回写主档 valid_to（延续 RENEW） */
    private LocalDate newValidTo;
    /** 经办人 */
    private Long ownerBy;
    /** 备注 */
    private String remark;
    /** 乐观锁版本（@Version） */
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
