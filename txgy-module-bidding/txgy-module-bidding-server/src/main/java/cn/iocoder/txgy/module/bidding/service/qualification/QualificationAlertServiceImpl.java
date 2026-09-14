package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertConfigDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStaffConfigMapper;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.enums.CertNotifyBizTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertNotifySendStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertLevelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.QualificationStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.SafetyPermitStatusEnum;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
import java.util.Map;

/**
 * 资质类到期预警扫描实现（QUAL_EXPIRY 三源 → cert_alert_records + outbox 同事务）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十五-到期预警复用（三类 valid/reg 到期纳入 cert_alert
 * 域，经 cert_notify_outbox 可靠送达）；§九（档位映射/幂等范式）。
 * <p>设计遵循/针对：① 记录行 cert_id 语义归 certificate_info → 资质类用 DDL 泛化 ref_type/ref_id/ref_name
 * 定位（2026-09-10 ALTER），幂等键 ref_type:ref_id:yyyyMMdd；② 阈值配置取 alert_type=QUAL_EXPIRY 全局行，
 * 缺省回落平台默认 [7,30,60,90]（与 EXPIRY 同范式）；③ ≤30 天联动：资质/安许状态 VALID→EXPIRING_SOON
 * （先版本 CAS 更新，失败抛数据修改错误防吞并发），人员无状态仅提醒；④ 扫描同事务内插入多源记录，同日重跑幂等。
 */
@Slf4j
@Service
public class QualificationAlertServiceImpl implements QualificationAlertService {

    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(7, 30, 60, 90);
    private static final List<String> DEFAULT_CHANNELS = List.of("EMAIL", "IN_APP");
    private static final String DEFAULT_RECEIVER = "TENANT_ADMIN";
    private static final int EXPIRING_SOON_DAYS = 30;
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private CertAlertConfigMapper certAlertConfigMapper;
    @Resource
    private CertAlertRecordMapper certAlertRecordMapper;
    @Resource
    private CertNotifyOutboxMapper certNotifyOutboxMapper;
    @Resource
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    @Resource
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    @Resource
    private QualificationStaffConfigMapper qualificationStaffConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanQualificationExpiries(LocalDate today) {
        Config cfg = effectiveConfig();
        if (!cfg.enabled || cfg.thresholds.isEmpty()) {
            return 0;
        }
        int maxDays = cfg.thresholds.get(cfg.thresholds.size() - 1);
        int created = 0;
        String dayKey = KEY_DATE.format(today);

        // 源一：企业资质主档 valid_to
        for (EnterpriseQualificationDO qual : enterpriseQualificationMapper.selectExpiringCandidates(today, maxDays)) {
            if (qual.getValidTo() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, qual.getValidTo());
            if (days <= 0) {
                continue;
            }
            String level = levelFor(days, cfg.thresholds);
            String idempotentKey = "QUAL:" + qual.getId() + ":" + CertificateAlertTypeEnum.QUAL_EXPIRY.name()
                    + ":" + dayKey;
            if (certAlertRecordMapper.selectByIdempotentKey(idempotentKey) != null) {
                continue;
            }
            String name = qualDisplayName(qual);
            created += createQualAlertAndOutbox("QUAL", qual.getId(), name, days, level, idempotentKey, cfg, today);
            // ≤30 天且未进 EXPIRING_SOON → 状态机联动（VALID/EXPIRING_SOON→EXPIRING_SOON 同态跳过）
            if (days <= EXPIRING_SOON_DAYS && !QualificationStatusEnum.EXPIRING_SOON.name()
                    .equals(qual.getQualStatus())) {
                qual.setQualStatus(QualificationStatusEnum.EXPIRING_SOON.name());
                if (enterpriseQualificationMapper.updateById(qual) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
                }
            }
        }
        // 源二：安全生产许可证 valid_to
        for (QualificationSafetyPermitDO permit : qualificationSafetyPermitMapper.selectExpiringCandidates(today, maxDays)) {
            if (permit.getValidTo() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, permit.getValidTo());
            if (days <= 0) {
                continue;
            }
            String level = levelFor(days, cfg.thresholds);
            String idempotentKey = "SAFETY_PERMIT:" + permit.getId() + ":" + CertificateAlertTypeEnum.QUAL_EXPIRY.name()
                    + ":" + dayKey;
            if (certAlertRecordMapper.selectByIdempotentKey(idempotentKey) != null) {
                continue;
            }
            String name = "安全生产许可证 " + permit.getPermitNo();
            created += createQualAlertAndOutbox("SAFETY_PERMIT", permit.getId(), name, days, level,
                    idempotentKey, cfg, today);
            if (days <= EXPIRING_SOON_DAYS && !SafetyPermitStatusEnum.EXPIRING_SOON.name()
                    .equals(permit.getPermitStatus())) {
                permit.setPermitStatus(SafetyPermitStatusEnum.EXPIRING_SOON.name());
                if (qualificationSafetyPermitMapper.updateById(permit) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
                }
            }
        }
        // 源三：资质人员注册证 reg_valid_to（无状态字段，仅提醒；reg_valid_to NULL 已不满足由要素核查治理）
        for (QualificationStaffConfigDO staff : qualificationStaffConfigMapper.selectExpiringStaff(today, maxDays)) {
            if (staff.getRegValidTo() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, staff.getRegValidTo());
            if (days <= 0) {
                continue;
            }
            String level = levelFor(days, cfg.thresholds);
            String idempotentKey = "STAFF:" + staff.getId() + ":" + CertificateAlertTypeEnum.QUAL_EXPIRY.name()
                    + ":" + dayKey;
            if (certAlertRecordMapper.selectByIdempotentKey(idempotentKey) != null) {
                continue;
            }
            String name = "人员注册 " + staff.getPersonName() + "（" + staff.getRegNo() + "）";
            created += createQualAlertAndOutbox("STAFF", staff.getId(), name, days, level, idempotentKey, cfg, today);
        }
        return created;
    }

    // ========== 内部 ==========

    private String qualDisplayName(EnterpriseQualificationDO qual) {
        String grade = qual.getQualGrade() == null ? "" : qual.getQualGrade();
        String cat = qual.getQualCategory() == null ? "" : qual.getQualCategory();
        return grade + cat + "资质 " + qual.getCertNo();
    }

    private Config effectiveConfig() {
        CertAlertConfigDO row = certAlertConfigMapper.selectGlobal(CertificateAlertTypeEnum.QUAL_EXPIRY.name());
        if (row == null) {
            return new Config(true, new ArrayList<>(DEFAULT_THRESHOLDS), DEFAULT_CHANNELS);
        }
        List<Integer> thresholds = parseInts(row.getThresholds(), DEFAULT_THRESHOLDS);
        List<String> channels = parseStrs(row.getNotifyChannels(), DEFAULT_CHANNELS);
        return new Config(Boolean.TRUE.equals(row.getEnabled()), thresholds, channels);
    }

    private int createQualAlertAndOutbox(String refType, Long refId, String refName, long days, String level,
                                         String idempotentKey, Config cfg, LocalDate today) {
        String message = String.format("【资质类到期】%s 将于 %d 天后到期（%s），请及时办理延续/续期",
                refName, days, today.plusDays(days));
        CertAlertRecordDO record = new CertAlertRecordDO();
        record.setAlertType(CertificateAlertTypeEnum.QUAL_EXPIRY.name());
        record.setRefType(refType);
        record.setRefId(refId);
        record.setRefName(refName);
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
                "alertType", CertificateAlertTypeEnum.QUAL_EXPIRY.name(),
                "alertLevel", level,
                "alertMessage", message,
                "refType", refType,
                "refId", refId,
                "refName", refName,
                "validTo", String.valueOf(today.plusDays(days)));
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

    private String levelFor(long daysLeft, List<Integer> thresholdsAsc) {
        if (daysLeft <= thresholdsAsc.get(0)) {
            return CertificateAlertLevelEnum.CRITICAL.name();
        }
        if (thresholdsAsc.size() >= 2 && daysLeft <= thresholdsAsc.get(1)) {
            return CertificateAlertLevelEnum.WARNING.name();
        }
        return CertificateAlertLevelEnum.INFO.name();
    }

    private List<Integer> parseInts(String json, List<Integer> fallback) {
        try {
            List<Integer> list = objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Integer.class));
            List<Integer> sorted = new ArrayList<>(list);
            java.util.Collections.sort(sorted);
            return sorted;
        } catch (Exception e) {
            return new ArrayList<>(fallback);
        }
    }

    private List<String> parseStrs(String json, List<String> fallback) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
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

    private record Config(boolean enabled, List<Integer> thresholds, List<String> channels) {
    }
}
