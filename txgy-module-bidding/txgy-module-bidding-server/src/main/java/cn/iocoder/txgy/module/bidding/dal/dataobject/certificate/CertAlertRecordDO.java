package cn.iocoder.txgy.module.bidding.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预警记录（T 轨 · 幂等防重跑）。表：cert_alert_records（DDL 见 sql/history/2026-09-05-cert-alert.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九：idempotent_key UNIQUE 按
 * (cert_id, alert_type, 触发日期) 生成，定时任务重启/重跑不重复；alert_level 档位；notify_status 与
 * cert_notify_outbox 联动；handled/version 处理闭环 CAS。
 */
@TableName("cert_alert_records")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertAlertRecordDO extends TenantBaseDO {

    /** 主键（雪花 ID；=设计 alert_id） */
    private Long id;
    /** 关联证照 certificate_info.id（EXPIRY 等证照类填；QUAL_EXPIRY 资质类置 NULL，用 ref_* 定位，DDL 2026-09-10） */
    private Long certId;
    /** 业务对象大类 code（QUAL_EXPIRY 用）：QUAL 企业资质/SAFETY_PERMIT 安许/STAFF 资质人员 */
    private String refType;
    /** 业务对象主键（qual_id/permit_id/staff_id） */
    private Long refId;
    /** 业务对象显示名（脱敏） */
    private String refName;
    /** 预警类型 code：EXPIRY/REVIEW/RENEWAL */
    private String alertType;
    /** 档位 code：INFO/WARNING/CRITICAL */
    private String alertLevel;
    /** 幂等键：cert_id:alert_type:yyyyMMdd */
    private String idempotentKey;
    /** 预警内容 */
    private String alertMessage;
    /** 触发时间 */
    private LocalDateTime triggeredAt;
    /** 实际通知渠道（JSON 数组字符串，镜像配置快照） */
    private String notifyChannel;
    /** 接收人 ID/角色（脱敏） */
    private String notifyTarget;
    /** 通知状态 code：PENDING/SENDING/SUCCESS/FAILED/DEAD */
    private String notifyStatus;
    /** 是否已处理 */
    private Boolean handled;
    /** 处理人 */
    private Long handledBy;
    /** 处理时间 */
    private LocalDateTime handledAt;
    /** 处理备注 */
    private String handleNote;
    /** 归档状态：0 在库 /1 已归档 */
    private Integer archiveStatus;
    /** 乐观锁版本（@Version） */
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
