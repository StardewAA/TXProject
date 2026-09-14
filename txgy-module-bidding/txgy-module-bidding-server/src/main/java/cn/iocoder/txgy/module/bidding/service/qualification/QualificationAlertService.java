package cn.iocoder.txgy.module.bidding.service.qualification;

import java.time.LocalDate;

/**
 * 资质类到期预警扫描（QUAL_EXPIRY：三源并入 cert_alert 域）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十五-到期预警复用：enterprise_qualification.valid_to /
 * qualification_safety_permit.valid_to / qualification_staff_config.reg_valid_to 一并纳入
 * cert_alert_config/cert_alert_records 扫描（alert_type=QUAL_EXPIRY），经 cert_notify_outbox 可靠送达；
 * 与 §九 证照 EXPIRY 扫描共用档位映射/幂等键范式（资质行幂等键 ref_type:ref_id:yyyyMMdd）。
 */
public interface QualificationAlertService {

    /**
     * 扫描三源到期对象并生成 QUAL_EXPIRY 预警（同日幂等，重跑安全）：
     * 返回本次新建预警记录数。≤30 天且主档/安许状态 VALID 或 EXPIRING_SOON 时联动状态机。
     */
    int scanQualificationExpiries(LocalDate today);
}
