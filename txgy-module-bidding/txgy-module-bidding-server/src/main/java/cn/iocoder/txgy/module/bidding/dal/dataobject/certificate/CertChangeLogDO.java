package cn.iocoder.txgy.module.bidding.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证照变更审计日志（T 轨 · 只追加 · 等保 2.0 三级）。表：cert_change_logs（DDL 见 sql/2026-09-04-certificate.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §13＋§18.3 v3.4 对齐；《招投标模块开发规范》§5 六-3。
 * <p>设计遵循/针对：① record_hash=SHA-256(字段拼接)＋prev_hash 哈希链，链首 prev_hash=ROOT，根哈希定期固化
 * 独立审计库（v3.3 §13）；② 防并发重复追加由 UK(cert_id, prev_hash, …) 链式保证（前驱唯一），写日志与主表更新同事务；
 * ③ 只追加：业务不置 deleted、账号收回 UPDATE/DELETE；④ 保留期 ≥3 年，冷数据由归档任务按月清理（v3.4 §18.3-2）。
 */
@TableName("cert_change_logs")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertChangeLogDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 关联证照（冗余父键 certificate_info.id） */
    private Long certId;
    /** 变更字段名 */
    private String fieldName;
    /** 变更前值（CREATE 行为空） */
    private String oldValue;
    /** 变更后值 */
    private String newValue;
    /** 变更类型 code：CREATE/UPDATE/STATUS_CHANGE/REVOKE */
    private String changeType;
    /** 操作来源 code：MANUAL/IMPORT/OCR/API_SYNC/SYSTEM */
    private String opSource;
    /** 操作 IP（支持 IPv6） */
    private String opIp;
    /** 批次号（整批导入/回滚归组） */
    private String batchId;
    /** 本行哈希 SHA-256（v3.3 §13 字段拼接口径） */
    private String recordHash;
    /** 哈希链前驱 record_hash（链首=ROOT） */
    private String prevHash;
}
