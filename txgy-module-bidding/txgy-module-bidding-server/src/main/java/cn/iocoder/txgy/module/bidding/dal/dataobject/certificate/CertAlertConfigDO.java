package cn.iocoder.txgy.module.bidding.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 到期/年检/续期预警配置（T 轨）。表：cert_alert_config（DDL 见 sql/history/2026-09-05-cert-alert.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九（cert_type IS NULL=全局；alert_type；
 * thresholds 默认 [90,60,30,7]；enabled；notify_channels 默认 ["EMAIL","IN_APP"]）。
 * <p>设计遵循/针对：① JSON 列以字符串存储（开发规范 §5 三-7/三-3 JSON 允许）；② 平台默认（无租户配置行）
 * 由服务层默认常量承担（v3.3 的 tenant_id NULL=平台默认），租户只存差异化行；
 * ③ version @Version 乐观锁（开发规范 §4.5），更新走 CAS。
 */
@TableName("cert_alert_config")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertAlertConfigDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 差异化配置的证照大类 code；null=全局 */
    private String certType;
    /** 预警类型 code：EXPIRY/REVIEW/RENEWAL */
    private String alertType;
    /** 提前天数多级阈值（JSON 数组字符串，如 [90,60,30,7]） */
    private String thresholds;
    /** 开关 */
    private Boolean enabled;
    /** 通知渠道（JSON 数组字符串，如 ["EMAIL","IN_APP"]） */
    private String notifyChannels;
    /** 乐观锁版本（@Version，开发规范 §4.5） */
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
