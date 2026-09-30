package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateAlertPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateAlertRespVO;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证照到期/年检/续期预警服务。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九（预警逻辑：定时任务扫 valid_to 命中 thresholds
 * 写 alert_records 并通知）＋§十一 表12（发件箱可靠投递）；51 号 ZZZT（EXPIRING_SOON 语义）；
 * 《招投标模块开发规范》§4.5（记录/配置乐观锁）。
 */
public interface CertificateAlertService {

    /**
     * 到期预警扫描（按租户执行；job 层以 TenantUtils 逐租户调用本方法）：
     * 1) 取租户全局配置（无配置行用平台默认 [90,60,30,7]/[EMAIL,IN_APP]/开启）；
     * 2) 扫 ACTIVE/EXPIRING_SOON 且 valid_to 落在今日+max 阈值窗内的证照；
     * 3) 档位映射：≤7 天 CRITICAL、≤30 天 WARNING、其余（≤60/90）INFO（v3.3 §九）；
     * 4) 幂等键 cert_id:EXPIRY:yyyyMMdd 防重跑；落 cert_alert_records 并按渠道写 cert_notify_outbox（同事务）；
     * 5) ACTIVE 且 ≤30 天时经状态机置 EXPIRING_SOON（日志走 STATUS_CHANGE 哈希链审计）。
     * 返回新建预警记录数。
     */
    int scanExpiryAlerts(LocalDate today);

    /**
     * 发件箱派发（最多 limit 条，本租户）：PENDING/FAILED 且到重试时间 → SENDING → 渠道发送 →
     * 成功 SUCCESS（回写预警记录 notify_status）；失败按 1/5/30 分、2/6 时退避，超 max_retry 转 DEAD 死信。
     * 返回派发处理条数。
     */
    int dispatchPending(int limit, LocalDateTime now);

    /**
     * 处理预警（如“已续期”）：乐观锁 CAS；版本不符抛 CERT_ALERT_RECORD_MODIFIED。
     */
    void markAlertHandled(Long alertId, Integer version, Long handledBy, String handleNote);

    /**
     * 证照到期预警清单分页。
     *
     * <p>规范出处：《电子证照模块-前端接口契约》§2 + 资质域既有清单形状（三域同形）。
     *
     * <p>为什么需要：决策 ③——证照域此前**只有扫描/派发、没有读接口**，预警发出去之后页面上看不到、也筛不出待处理。
     *
     * <p>业务目标：支撑"到期预警看板"（含按企业、按证照、按档位、按是否已处理筛选）。
     *
     * @param reqVO 分页与筛选条件
     * @return 预警记录分页（含 version，供后续处理时做 CAS）
     */
    PageResult<CertificateAlertRespVO> getAlertPage(CertificateAlertPageReqVO reqVO);
}
