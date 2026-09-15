package cn.iocoder.txgy.module.bidding.service.personnel;

import java.time.LocalDate;
import java.util.Map;

/**
 * 员工证件到期预警扫描（员工证书有效期 / 证书复审 / 身份证有效期 → cert_alert 域）。
 *
 * <p>规范出处：用户业务口径"设置证书到期自动提醒功能，在人员资质证书、安全生产证、身份证有效期到期前
 * 自动提醒相关管理人员"；《电子证件全流程管理_数据库设计.md》v3.3 §九（预警档位、幂等键、经
 * cert_notify_outbox 可靠送达）、§十五-到期预警复用（资质三源并入先例）；
 * 《资质管理/06-电子证照设计材料对比与推进依据-2026-09-10.md》§二-4（复审/年审是与到期日独立的时间维度）、
 * §二-5（法定预警下限：建造师延续预留继续教育 90 天、特设生产许可换证 180 天等，不得被调低错过窗口）。
 * <p>设计遵循/针对：① 复用 cert_alert_records/cert_notify_outbox，记录行以 ref_type/ref_id 泛化定位
 * （与 QUAL_EXPIRY 先例一致），不新建预警表；② 幂等键 ref_type:ref_id:alert_type:yyyyMMdd，定时任务重跑安全；
 * ③ ≤30 天联动 personnel_certificate.cert_status → EXPIRING_SOON（VALIS 才可迁，走 CAS）；
 * ④ 法定下限参与档位判定：进入法定窗口至少 WARNING，进入 30 天内 CRITICAL——法定窗口优先于平台配置。
 * <p>业务目标：在证书/身份证失效前把人找到并提醒到位，避免"投标当日才发现证过期"。
 */
public interface PersonnelAlertService {

    /**
     * 扫描员工证书有效期、证书复审、身份证有效期三类到期对象并生成预警（同日幂等，重跑安全）。
     *
     * @param today 基准日（便于测试与补跑历史日期）
     * @return 本次新建的预警记录数
     */
    int scanPersonnelExpiries(LocalDate today);

    /**
     * 法定预警下限表（certType/subType 关键字 → 最少提前天数）。
     *
     * <p>规范出处：06 文档 §二-5（排污许可届满前 60 日、司炉证复审 60 日、特设生产许可证换证 180 日、
     * 安许证延期 90 日、一级建造师延续预留继续教育 90 日）。
     * <p>业务目标：供前端在配置预警天数时提示"不得低于法定下限"，从源头避免错过法定办理窗口。
     */
    Map<String, Integer> legalFloorDays();
}
