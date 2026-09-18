package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertConfigDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertNotifyBizTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertNotifySendStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertLevelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 证照到期预警服务实现（T 轨；扫描/派发均为按租户执行的定时任务业务方法）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九（预警配置/记录/档位映射/幂等键）+§十一 表12
 * （发件箱可靠投递：PENDING→SENDING→SUCCESS/FAILED 退避重试→DEAD）；51号 ZZZT（≤30 天置 EXPIRING_SOON）；
 * 《招投标模块开发规范》§4.5（config/records 乐观锁）。
 * <p>设计遵循/针对：① 平台默认常量承担 v3.3“tenant_id NULL=平台默认”行（租户零配置即可用）；
 * ② 幂等键 cert_id:alert_type:yyyyMMdd（v3.3 §九 文案口径）；③ 记录与发件箱同事务写入，
 * 通知“不丢不重”（表12）；④ 发送失败指数退避 1/5/30 分、2/6 时，超限转 DEAD 死信并告警（表12）；
 * ⑤ 渠道发送经 CertNotifyChannelSender 接口隔离（开发规范 §6），默认日志落点闭环。
 */
@Slf4j
@Service
public class CertificateAlertServiceImpl implements CertificateAlertService {

    /** 平台默认阈值（天），升序存储：≤7→CRITICAL、≤30→WARNING、其余（≤60/90）→INFO（v3.3 §九） */
    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(7, 30, 60, 90);
    /** 平台默认通知渠道 */
    private static final List<String> DEFAULT_CHANNELS = List.of("EMAIL", "IN_APP");
    /** 默认接收人占位（对接用户中心后由配置/组织架构决定，见 DDL 注释） */
    private static final String DEFAULT_RECEIVER = "TENANT_ADMIN";
    /** 到期预警扫描 ≤30 天时置 EXPIRING_SOON（51号 ZZZT 口径，台账自查只认 ACTIVE） */
    private static final int EXPIRING_SOON_DAYS = 30;
    /** 重试退避间隔（分）：1/5/30 分、2/6 时（v3.3 §十一 表12） */
    private static final int[] BACKOFF_MINUTES = {1, 5, 30, 120, 360};
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private CertAlertConfigMapper certAlertConfigMapper;
    @Resource
    private CertAlertRecordMapper certAlertRecordMapper;
    @Resource
    private CertNotifyOutboxMapper certNotifyOutboxMapper;
    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private CertificateService certificateService;
    @Resource
    private CertNotifyChannelSender certNotifyChannelSender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanExpiryAlerts(LocalDate today) {
        Config cfg = effectiveConfig();
        if (!cfg.enabled || cfg.thresholds.isEmpty()) {
            return 0;
        }
        List<CertificateInfoDO> candidates = certificateInfoMapper.selectExpiringCandidates(today,
                cfg.thresholds.get(cfg.thresholds.size() - 1));
        int created = 0;
        for (CertificateInfoDO cert : candidates) {
            if (cert.getValidTo() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, cert.getValidTo());
            if (days <= 0) {
                continue; // 已到期交由过期处理任务（后续里程碑），不在此预警
            }
            String level = levelFor(days, cfg.thresholds);
            String idempotentKey = cert.getId() + ":" + CertificateAlertTypeEnum.EXPIRY.name() + ":"
                    + KEY_DATE.format(today);
            if (certAlertRecordMapper.selectByIdempotentKey(idempotentKey) != null) {
                continue; // 当日已触发，重跑幂等跳过
            }
            created += createAlertAndOutbox(cert, days, level, idempotentKey, cfg, today);
            if (CertificateStatusEnum.ACTIVE.name().equals(cert.getCertStatus())
                    && days <= EXPIRING_SOON_DAYS) {
                certificateService.changeStatus(cert.getId(), CertificateStatusEnum.EXPIRING_SOON.name(),
                        cert.getVersion(), "到期预警扫描（≤" + EXPIRING_SOON_DAYS + " 天）",
                        CertificateChangeSourceEnum.SYSTEM, null);
            }
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int dispatchPending(int limit, LocalDateTime now) {
        List<CertNotifyOutboxDO> rows = certNotifyOutboxMapper.selectDispatchable(now, limit);
        for (CertNotifyOutboxDO row : rows) {
            row.setSendStatus(CertNotifySendStatusEnum.SENDING.name());
            certNotifyOutboxMapper.updateById(row);
            try {
                certNotifyChannelSender.send(row.getChannel(), row.getReceiver(), row.getPayload());
                row.setSendStatus(CertNotifySendStatusEnum.SUCCESS.name());
                row.setSentAt(now);
                row.setNextRetryAt(null);
                row.setLastError(null);
                certNotifyOutboxMapper.updateById(row);
                if (CertNotifyBizTypeEnum.ALERT.name().equals(row.getBizType())) {
                    certAlertRecordMapper.updateNotifyStatus(row.getBizId(),
                            CertNotifySendStatusEnum.SUCCESS.name());
                }
            } catch (Exception e) {
                log.warn("[cert-notify][outbox={}] 发送失败：{}", row.getId(), e.getMessage());
                int retried = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
                row.setRetryCount(retried);
                if (retried >= (row.getMaxRetry() == null ? 5 : row.getMaxRetry())) {
                    row.setSendStatus(CertNotifySendStatusEnum.DEAD.name()); // 死信转人工+告警
                } else {
                    row.setSendStatus(CertNotifySendStatusEnum.FAILED.name());
                    row.setNextRetryAt(now.plusMinutes(backoffMinutes(retried)));
                }
                row.setLastError(e.getMessage() == null ? "unknown" : e.getMessage());
                certNotifyOutboxMapper.updateById(row);
            }
        }
        return rows.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAlertHandled(Long alertId, Integer version, Long handledBy, String handleNote) {
        CertAlertRecordDO record = certAlertRecordMapper.selectById(alertId);
        if (record == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_ALERT_RECORD_NOT_EXISTS);
        }
        if (version == null || !version.equals(record.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_ALERT_RECORD_MODIFIED);
        }
        record.setHandled(Boolean.TRUE);
        record.setHandledBy(handledBy);
        record.setHandledAt(LocalDateTime.now());
        record.setHandleNote(handleNote);
        if (certAlertRecordMapper.updateById(record) == 0) { // @Version CAS 兜底
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_ALERT_RECORD_MODIFIED);
        }
    }

    // ========== 内部工具 ==========

    /** 有效配置（含平台默认回落）。 */
    private Config effectiveConfig() {
        CertAlertConfigDO row = certAlertConfigMapper.selectGlobal(CertificateAlertTypeEnum.EXPIRY.name());
        if (row == null) {
            return new Config(true, new ArrayList<>(DEFAULT_THRESHOLDS), DEFAULT_CHANNELS);
        }
        List<Integer> thresholds = parseInts(row.getThresholds(), DEFAULT_THRESHOLDS);
        List<String> channels = parseStrs(row.getNotifyChannels(), DEFAULT_CHANNELS);
        return new Config(Boolean.TRUE.equals(row.getEnabled()), thresholds, channels);
    }

    /** 建预警记录 + 每渠道一条发件箱（同事务；幂等键已查重）。返回 1。 */
    private int createAlertAndOutbox(CertificateInfoDO cert, long days, String level, String idempotentKey,
                                     Config cfg, LocalDate today) {
        String message = String.format("证照《%s》（编号对外脱敏）将于 %d 天后到期（有效至 %s），请及时续期/更新",
                cert.getCertName(), days, cert.getValidTo());
        CertAlertRecordDO record = new CertAlertRecordDO();
        record.setCertId(cert.getId());
        record.setAlertType(CertificateAlertTypeEnum.EXPIRY.name());
        record.setAlertLevel(level);
        record.setIdempotentKey(idempotentKey);
        record.setAlertMessage(message);
        record.setTriggeredAt(LocalDateTime.now());
        record.setNotifyChannel(writeJson(cfg.channels));
        record.setNotifyTarget(DEFAULT_RECEIVER);
        record.setNotifyStatus(CertNotifySendStatusEnum.PENDING.name());
        certAlertRecordMapper.insert(record);
        Map<String, Object> payload = Map.of(
                "bizType", CertNotifyBizTypeEnum.ALERT.name(),
                "alertLevel", level,
                "alertMessage", message,
                "certName", cert.getCertName() == null ? "" : cert.getCertName(),
                "certNo", cert.getCertNo() == null ? "" : cert.getCertNo(),
                "validTo", String.valueOf(cert.getValidTo()));
        for (String channel : cfg.channels) {
            CertNotifyOutboxDO outbox = new CertNotifyOutboxDO();
            outbox.setBizType(CertNotifyBizTypeEnum.ALERT.name());
            outbox.setBizId(record.getId());
            outbox.setChannel(channel);
            outbox.setReceiver(DEFAULT_RECEIVER);
            outbox.setPayload(writeJson(payload));
            outbox.setSendStatus(CertNotifySendStatusEnum.PENDING.name());
            outbox.setRetryCount(0);
            outbox.setMaxRetry(5);
            certNotifyOutboxMapper.insert(outbox);
        }
        return 1;
    }

    /** 档位映射（v3.3 §九）：阈值升序后 ≤t0→CRITICAL、≤t1→WARNING、否则 INFO。 */
    private String levelFor(long daysLeft, List<Integer> thresholdsAsc) {
        if (daysLeft <= thresholdsAsc.get(0)) {
            return CertificateAlertLevelEnum.CRITICAL.name();
        }
        if (thresholdsAsc.size() >= 2 && daysLeft <= thresholdsAsc.get(1)) {
            return CertificateAlertLevelEnum.WARNING.name();
        }
        return CertificateAlertLevelEnum.INFO.name();
    }

    private int backoffMinutes(int retried) {
        int idx = Math.min(retried - 1, BACKOFF_MINUTES.length - 1);
        return BACKOFF_MINUTES[Math.max(idx, 0)];
    }

    private List<Integer> parseInts(String json, List<Integer> fallback) {
        try {
            List<Integer> list = objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Integer.class));
            List<Integer> sorted = new ArrayList<>(list);
            Collections.sort(sorted);
            return sorted;
        } catch (Exception e) {
            log.warn("[cert-alert] 阈值 JSON 解析失败，用默认：{}", json);
            return new ArrayList<>(fallback);
        }
    }

    private List<String> parseStrs(String json, List<String> fallback) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("[cert-alert] 渠道 JSON 解析失败，用默认：{}", json);
            return new ArrayList<>(fallback);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /** 配置视图（默认常量与租户行归一）。 */
    private record Config(boolean enabled, List<Integer> thresholds, List<String> channels) {
    }
}
