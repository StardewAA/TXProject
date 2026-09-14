package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertConfigDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.CertNotifyBizTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertNotifySendStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertLevelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.PersonnelCertStatusEnum;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工证件到期预警扫描实现（三类来源 → cert_alert_records + outbox 同事务 + ≤30 天状态联动）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九（档位/幂等/送达）、§十五（泛化 ref_* 先例）；
 * 06 文档 §二-4（复审独立维度）、§二-5（法定下限优先）；
 * 《招投标模块开发规范》§4.5（状态联动走 CAS 更新，失败即抛数据修改异常，不吞并发）。
 * <p>设计遵循/针对：① 复用 alert_type=EXPIRY（人员证件到期同属"证件有效期"语义）与 REVIEW（复审/年审），
 * 用 ref_type 区分 PERSON_CERT / PERSON_ID_CARD；② 幂等键含触发日期，同日重跑不重复推送；
 * ③ 法定下限参与档位：进入法定窗口至少 WARNING、进入 30 天内 CRITICAL（平台阈值只能更早不能更晚）；
 * ④ 员工证书 ≤30 天联动 cert_status → EXPIRING_SOON，仅从 VALID 迁（CAS 失败抛错，防止并发覆盖）。
 * <p>业务目标：证书/身份证在失效前被提醒到位，且"复审逾期"这种非到期失效也能被提前发现。
 */
@Slf4j
@Service
public class PersonnelAlertServiceImpl implements PersonnelAlertService {

    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(7, 30, 60, 90);
    private static final List<String> DEFAULT_CHANNELS = List.of("EMAIL", "IN_APP");
    private static final String DEFAULT_RECEIVER = "TENANT_ADMIN";
    private static final int EXPIRING_SOON_DAYS = 30;
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** 泛化定位类型码：员工证书 / 员工证书复审 / 身份证。 */
    private static final String REF_PERSON_CERT = "PERSON_CERT";
    private static final String REF_PERSON_CERT_REVIEW = "PERSON_CERT_REVIEW";
    private static final String REF_PERSON_ID_CARD = "PERSON_ID_CARD";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private CertAlertConfigMapper certAlertConfigMapper;
    @Resource
    private CertAlertRecordMapper certAlertRecordMapper;
    @Resource
    private CertNotifyOutboxMapper certNotifyOutboxMapper;
    @Resource
    private PersonnelCertificateMapper personnelCertificateMapper;
    @Resource
    private PersonnelProfileMapper personnelProfileMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanPersonnelExpiries(LocalDate today) {
        Config cfg = effectiveConfig();
        if (!cfg.enabled || cfg.thresholds.isEmpty()) {
            return 0;
        }
        int created = 0;
        String dayKey = KEY_DATE.format(today);

        // 源一：员工证书有效期（证书类到期；建造师注册证书 3 年、安管证等）
        for (PersonnelCertificateDO cert : personnelCertificateMapper.selectExpiringCandidates(today,
                cfg.maxDays())) {
            if (cert.getValidTo() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, cert.getValidTo());
            if (days <= 0) {
                continue;
            }
            String key = REF_PERSON_CERT + ":" + cert.getId() + ":"
                    + CertificateAlertTypeEnum.EXPIRY.name() + ":" + dayKey;
            if (certAlertRecordMapper.selectByIdempotentKey(key) != null) {
                continue;
            }
            String name = cert.getCertName() == null ? cert.getCertNo() : cert.getCertName();
            String level = levelFor(days, cfg.thresholds, legalFloorDays(cert.getCertType(), cert.getCertSubType()));
            created += createAlertAndOutbox(CertificateAlertTypeEnum.EXPIRY.name(), REF_PERSON_CERT, cert.getId(),
                    name, days, level, key, cfg, today,
                    String.format("【员工证书到期】%s 将于 %d 天后到期（%s），请及时办理延续/换证", name, days,
                            today.plusDays(days)));
            // ≤30 天且当前为 VALID → 联动 EXPIRING_SOON（与资质/安许扫描同范式）
            if (days <= EXPIRING_SOON_DAYS && PersonnelCertStatusEnum.VALID.name().equals(cert.getCertStatus())) {
                cert.setCertStatus(PersonnelCertStatusEnum.EXPIRING_SOON.name());
                if (personnelCertificateMapper.updateById(cert) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
                }
            }
        }

        // 源二：员工证书复审/年审（与到期日独立：司炉证届满前 1 个月复审，逾期证书会被暂停）
        for (PersonnelCertificateDO cert : personnelCertificateMapper.selectReviewDueCandidates(today,
                cfg.maxDays())) {
            if (cert.getNextReviewDate() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, cert.getNextReviewDate());
            if (days <= 0) {
                continue;
            }
            String key = REF_PERSON_CERT_REVIEW + ":" + cert.getId() + ":"
                    + CertificateAlertTypeEnum.REVIEW.name() + ":" + dayKey;
            if (certAlertRecordMapper.selectByIdempotentKey(key) != null) {
                continue;
            }
            String name = cert.getCertName() == null ? cert.getCertNo() : cert.getCertName();
            String level = levelFor(days, cfg.thresholds, 30);
            created += createAlertAndOutbox(CertificateAlertTypeEnum.REVIEW.name(), REF_PERSON_CERT_REVIEW,
                    cert.getId(), name, days, level, key, cfg, today,
                    String.format("【员工证书复审】%s 需在 %d 天内完成复审/年审（%s），逾期证书可能被暂停", name,
                            days, today.plusDays(days)));
        }

        // 源三：身份证有效期（无证书台账行，直接取档案；身份证过期则一切申报/投标材料无效）
        for (PersonnelProfileDO profile : personnelProfileMapper.selectExpiringIdCards(today, cfg.maxDays())) {
            if (profile.getIdCardValidTo() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, profile.getIdCardValidTo());
            if (days <= 0) {
                continue;
            }
            String key = REF_PERSON_ID_CARD + ":" + profile.getId() + ":"
                    + CertificateAlertTypeEnum.EXPIRY.name() + ":" + dayKey;
            if (certAlertRecordMapper.selectByIdempotentKey(key) != null) {
                continue;
            }
            String level = levelFor(days, cfg.thresholds, 90);
            created += createAlertAndOutbox(CertificateAlertTypeEnum.EXPIRY.name(), REF_PERSON_ID_CARD,
                    profile.getId(), profile.getPersonName(), days, level, key, cfg, today,
                    String.format("【身份证到期】员工 %s 的身份证将于 %d 天后到期（%s），请及时换证",
                            profile.getPersonName(), days, today.plusDays(days)));
        }
        log.info("[personnel-alert][scan] today={} created={}", today, created);
        return created;
    }

    @Override
    public Map<String, Integer> legalFloorDays() {
        // 出处：06 文档 §二-5（法定办理/复审窗口，配置的预警天数不得低于该值，否则会错过法定窗口）
        Map<String, Integer> floor = new LinkedHashMap<>();
        floor.put("REG_PRACTICE_CERT", 90);      // 一级建造师注册届满前 30 日申请延续 + 预留 120 学时继续教育
        floor.put("SAFETY_ASSESS_CERT", 90);     // 安全生产许可证期满前 3 个月申请延期（国务院令第397号）
        floor.put("SPECIAL_EQUIPMENT", 180);     // 特种设备生产许可证期满前 6 个月申请换证（TSG 07-2019）
        floor.put("SKILL_CERT", 60);             // 司炉等特种设备作业人员证届满前 1 个月复审（留缓冲）
        floor.put("POLLUTION_PERMIT", 60);       // 排污许可届满前 60 日申请延续
        floor.put("ID_CARD", 90);                // 身份证换发周期长（异地办理 + 邮寄），保守取 90 天
        return Collections.unmodifiableMap(floor);
    }

    // ========== 内部 ==========

    /** 取某证书类型对应的法定预警下限（无对应配置返回 null，表示仅按平台阈值提醒）。 */
    private Integer legalFloorDays(String certType, String certSubType) {
        Map<String, Integer> floor = legalFloorDays();
        Integer direct = certType == null ? null : floor.get(certType);
        if (direct != null) {
            return direct;
        }
        // 特种设备作业人员证按细分（G1/G2/G3）识别：细分以 G 开头即视为特种设备作业
        if (certSubType != null && certSubType.startsWith("G")) {
            return floor.get("SPECIAL_EQUIPMENT");
        }
        return null;
    }

    private Config effectiveConfig() {
        CertAlertConfigDO row = certAlertConfigMapper.selectGlobal(CertificateAlertTypeEnum.EXPIRY.name());
        if (row == null) {
            return new Config(true, new ArrayList<>(DEFAULT_THRESHOLDS), DEFAULT_CHANNELS);
        }
        return new Config(Boolean.TRUE.equals(row.getEnabled()), parseInts(row.getThresholds(), DEFAULT_THRESHOLDS),
                parseStrs(row.getNotifyChannels(), DEFAULT_CHANNELS));
    }

    private int createAlertAndOutbox(String alertType, String refType, Long refId, String refName, long days,
                                     String level, String idempotentKey, Config cfg, LocalDate today,
                                     String message) {
        CertAlertRecordDO record = new CertAlertRecordDO();
        record.setAlertType(alertType);
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
                "alertType", alertType,
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

    /**
     * 档位判定（平台阈值 + 法定下限取更严者）。
     *
     * <p>为什么不能让配置单方面决定：预警天数被调小时，法定办理窗口（如建造师延续需预留继续教育时间）
     * 会被错过，届时证书作废、投标资格受影响——法定下限是业务风险的硬边界。
     */
    private String levelFor(long daysLeft, List<Integer> thresholdsAsc, Integer legalFloor) {
        String byThreshold = daysLeft <= thresholdsAsc.get(0) ? CertificateAlertLevelEnum.CRITICAL.name()
                : (thresholdsAsc.size() >= 2 && daysLeft <= thresholdsAsc.get(1)
                        ? CertificateAlertLevelEnum.WARNING.name() : CertificateAlertLevelEnum.INFO.name());
        if (legalFloor == null) {
            return byThreshold;
        }
        if (daysLeft <= EXPIRING_SOON_DAYS) {
            return CertificateAlertLevelEnum.CRITICAL.name();
        }
        if (daysLeft <= legalFloor && CertificateAlertLevelEnum.INFO.name().equals(byThreshold)) {
            return CertificateAlertLevelEnum.WARNING.name();
        }
        return byThreshold;
    }

    private List<Integer> parseInts(String json, List<Integer> fallback) {
        try {
            List<Integer> list = objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Integer.class));
            List<Integer> sorted = new ArrayList<>(list);
            Collections.sort(sorted);
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
        int maxDays() {
            return thresholds.isEmpty() ? EXPIRING_SOON_DAYS : thresholds.get(thresholds.size() - 1);
        }
    }
}
