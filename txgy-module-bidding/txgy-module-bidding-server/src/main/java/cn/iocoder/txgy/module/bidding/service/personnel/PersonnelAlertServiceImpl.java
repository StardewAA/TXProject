package cn.iocoder.txgy.module.bidding.service.personnel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelAlertPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelAlertRespVO;

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
    /**
     * 读取审计的业务场景（写入 bidding_rule_config_audit.biz_scene）。
     *
     * <p>规范出处：《电子资质七大模块技术方案.md》§8.2（审计与追溯）。
     * <p>为什么这个扫描要留证：它会**改证书状态**（VALID → EXPIRING_SOON）并发预警通知，
     * 属于会对外产生结论的动作；租户申诉"为什么给我标成即将到期"时，需要证明当时用的窗口是多少、哪一版。
     */
    private static final String BIZ_SCENE_PERSONNEL_ALERT_SCAN = "PERSONNEL_ALERT_SCAN";
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
    @Resource
    private cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService ruleConfigService;

    /**
     * 取「临近到期」状态联动窗口（天）。
     *
     * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（阈值集中受控）/§8.2（读取留证）。
     * <p>为什么带审计读取：本值直接决定证书状态是否被改成 EXPIRING_SOON，是对外可见的结论。
     * 读取失败不影响取值（审计尽力而为），但取不到值会按 {@code RuleConfigKeys} 的代码兜底返回，
     * 与"表未初始化也要能跑"的既有约定一致。
     * <p>[2026-09-26 修复] 818eefe/并发回退时本方法被删、改回硬编码 30；按 e28380b 原文恢复。
     */
    private int resolveExpiringWindowDays() {
        Object value = ruleConfigService
                .resolveWithAudit(cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys.PERSONNEL_CERT_EXPIRING_WINDOW_DAYS,
                        BIZ_SCENE_PERSONNEL_ALERT_SCAN)
                .getValue();
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID,
                cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys.PERSONNEL_CERT_EXPIRING_WINDOW_DAYS);
    }

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
        // 「临近到期」状态联动窗口：来自规则配置中心（键 personnel.cert.expiring.window.days，缺配置回落代码兜底）。
        // 规范出处：《电子资质七大模块技术方案.md》§8.1（阈值集中受控、禁止硬编码）；
        //   配置中心种子 sql/history/2026-09-16-rule-config-center.sql 即本键，描述为
        //   "≤ 该值由预警扫描置 EXPIRING_SOON"，与源码原硬编码 30 同义。
        // 为什么必须接配置：原先硬编码 30 会让"改配置即改行为"变成假配置（运维改了不生效）；
        //   且同一语义窗口在证书/资质/材料三处各写一遍，改一处漏两处。读取走 resolveWithAudit 留证。
        int expiringWindowDays = resolveExpiringWindowDays();
        // 上界取"档位最大天数"与"状态流转窗口"的较大者（同证照扫描：只按档位会静默截断更大的窗口配置）
        int candidateUpperBound = Math.max(cfg.maxDays(), expiringWindowDays);
        for (PersonnelCertificateDO cert : personnelCertificateMapper.selectExpiringCandidates(today,
                candidateUpperBound)) {
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
            String level = levelFor(days, cfg.thresholds,
                    legalFloorDays(cert.getCertType(), cert.getCertSubType()), expiringWindowDays);
            created += createAlertAndOutbox(CertificateAlertTypeEnum.EXPIRY.name(), REF_PERSON_CERT, cert.getId(),
                    name, days, level, key, cfg, today,
                    String.format("【员工证书到期】%s 将于 %d 天后到期（%s），请及时办理延续/换证", name, days,
                            today.plusDays(days)));
            // ≤窗口且当前为 VALID → 联动 EXPIRING_SOON（与资质/安许扫描同范式）
            if (days <= expiringWindowDays && PersonnelCertStatusEnum.VALID.name().equals(cert.getCertStatus())) {
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
            String level = levelFor(days, cfg.thresholds, 30, expiringWindowDays);
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
            String level = levelFor(days, cfg.thresholds, 90, expiringWindowDays);
            created += createAlertAndOutbox(CertificateAlertTypeEnum.EXPIRY.name(), REF_PERSON_ID_CARD,
                    profile.getId(), profile.getPersonName(), days, level, key, cfg, today,
                    String.format("【身份证到期】员工 %s 的身份证将于 %d 天后到期（%s），请及时换证",
                            profile.getPersonName(), days, today.plusDays(days)));
        }
        log.info("[personnel-alert][scan] today={} created={}", today, created);
        return created;
    }

    @Override
    public PageResult<PersonnelAlertRespVO> getAlertPage(PersonnelAlertPageReqVO reqVO) {
        LambdaQueryWrapper<CertAlertRecordDO> query = new LambdaQueryWrapper<CertAlertRecordDO>()
                // 只取员工侧三类泛化预警；资质类（refType=QUAL 等）与证照类（refType 为空）不属于本清单
                .in(CertAlertRecordDO::getRefType, List.of(REF_PERSON_CERT, REF_PERSON_CERT_REVIEW, REF_PERSON_ID_CARD))
                .eq(reqVO.getRefType() != null, CertAlertRecordDO::getRefType, reqVO.getRefType())
                .eq(reqVO.getAlertType() != null, CertAlertRecordDO::getAlertType, reqVO.getAlertType())
                .eq(reqVO.getAlertLevel() != null, CertAlertRecordDO::getAlertLevel, reqVO.getAlertLevel())
                .eq(reqVO.getHandled() != null, CertAlertRecordDO::getHandled, reqVO.getHandled())
                .orderByDesc(CertAlertRecordDO::getTriggeredAt)
                .orderByDesc(CertAlertRecordDO::getId);
        // 按企业过滤：预警表以 refId（员工证书 id）定位，先取该企业员工证书 id 集合；空集合直接返回空页
        if (reqVO.getEntId() != null) {
            List<Long> certIds = personnelCertificateMapper.selectList(new LambdaQueryWrapper<PersonnelCertificateDO>()
                            .eq(PersonnelCertificateDO::getEntId, reqVO.getEntId()))
                    .stream().map(PersonnelCertificateDO::getId).toList();
            if (certIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L);
            }
            query.in(CertAlertRecordDO::getRefId, certIds);
        }
        Page<CertAlertRecordDO> page = certAlertRecordMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(PersonnelAlertServiceImpl::toAlertVO).toList(),
                page.getTotal());
    }

    private static PersonnelAlertRespVO toAlertVO(CertAlertRecordDO record) {
        PersonnelAlertRespVO vo = new PersonnelAlertRespVO();
        vo.setId(record.getId());
        vo.setVersion(record.getVersion());
        vo.setRefType(record.getRefType());
        vo.setRefId(record.getRefId());
        vo.setRefName(record.getRefName());
        vo.setAlertType(record.getAlertType());
        vo.setAlertLevel(record.getAlertLevel());
        vo.setAlertMessage(record.getAlertMessage());
        vo.setTriggeredAt(record.getTriggeredAt());
        vo.setHandled(record.getHandled());
        vo.setHandledBy(record.getHandledBy());
        vo.setHandledAt(record.getHandledAt());
        vo.setHandleNote(record.getHandleNote());
        return vo;
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
        // ===== 2026-09-13：与"对外接口/配置"共用一份来源（决策 ① 第 2 步）=====
        // 规范出处：契约 §1.2/§8.4 的法定下限接口，与运行期判定必须是同一份数据；
        //           《后端变更通知-给前端》§5 已记录"配置词表与运行期词表不一致"，本次按映射对齐。
        // 为什么这样写：配置键 cert.alert.legal.floor.days 用的是另一套类别名（SEWAGE_PERMIT/BOILER_REVIEW/SAFETY_PERMIT），
        //           而运行期 Map 的 key 是真实 cert_type（POLLUTION_PERMIT/SKILL_CERT/SAFETY_ASSESS_CERT）。
        //           直接读配置而不做映射 ⇒ 三类下限取不到值（比现在更糟）；故**显式映射 + 未映射即告警**。
        // 行为等价性：配置缺省（或无该键）时，完全回落到下面的硬编码值 ⇒ 判定结果与改造前逐项相同。
        Map<String, Integer> configured = readConfiguredLegalFloors();
        for (Map.Entry<String, Integer> e : configured.entrySet()) {
            String certType = LEGAL_FLOOR_ALIAS.getOrDefault(e.getKey(), e.getKey());
            if (!LEGAL_FLOOR_ALIAS.containsKey(e.getKey()) && !floor.containsKey(certType)) {
                // 配置里出现了运行期不认识的类别名：不静默忽略，明确告警（便于发现词表再次分叉）
                log.warn("[legal-floor] 配置中的类别 {} 未在运行期词表中登记，既非别名也非证照类型，已忽略", e.getKey());
                continue;
            }
            floor.put(certType, e.getValue());
        }
        return Collections.unmodifiableMap(floor);
    }

    // ========== 内部 ==========

        /**
     * 配置词表 → 运行期 cert_type 的对应关系（决策 ①：两份词表的分叉点）。
     *
     * <p>规范出处：契约 §1.2 的 cert.alert.legal.floor.days（配置侧）与运行期 legalFloorDays()（cert_type 侧）。
     *
     * <p>为什么需要它：同一件事在两处用了不同名字，而**配置键是唯一可运营修改的入口**，
     * 若不做映射直接读配置，这三类会取不到下限（静默失效，比硬编码更糟）。
     * 映射关系已列入《后端变更通知-给前端》§5，待业务/法务确认权威词表后可删除本表。
     */
    private static final Map<String, String> LEGAL_FLOOR_ALIAS = Map.of(
            "SEWAGE_PERMIT", "POLLUTION_PERMIT",   // 排污许可：配置侧叫 SEWAGE_PERMIT，运行期 cert_type 为 POLLUTION_PERMIT
            "BOILER_REVIEW", "SKILL_CERT",         // 司炉等作业人员证复审：配置侧 BOILER_REVIEW ↔ SKILL_CERT
            "SAFETY_PERMIT", "SAFETY_ASSESS_CERT", // 安全生产许可证：配置侧 SAFETY_PERMIT ↔ SAFETY_ASSESS_CERT
            "SPECIAL_EQUIPMENT", "SPECIAL_EQUIPMENT", // 两套同名，显式写出便于对照
            "REG_PRACTICE_CERT", "REG_PRACTICE_CERT",
            "ID_CARD", "ID_CARD"
    );

    /** 读配置侧下限；键不存在或解析失败一律返回空表 ⇒ 完全回落到硬编码默认（行为等价）。 */
    private Map<String, Integer> readConfiguredLegalFloors() {
        try {
            Map<String, Integer> configured = ruleConfigService.getIntMap(
                    cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys.CERT_ALERT_LEGAL_FLOOR_DAYS);
            return configured == null ? Map.of() : configured;
        } catch (Exception e) {
            log.warn("[legal-floor] 读取配置失败，回落到内置默认值：{}", e.getMessage());
            return Map.of();
        }
    }

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
    private String levelFor(long daysLeft, List<Integer> thresholdsAsc, Integer legalFloor, int expiringSoonDays) {
        String byThreshold = daysLeft <= thresholdsAsc.get(0) ? CertificateAlertLevelEnum.CRITICAL.name()
                : (thresholdsAsc.size() >= 2 && daysLeft <= thresholdsAsc.get(1)
                        ? CertificateAlertLevelEnum.WARNING.name() : CertificateAlertLevelEnum.INFO.name());
        if (legalFloor == null) {
            return byThreshold;
        }
        if (daysLeft <= expiringSoonDays) {
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
        /**
         * 扫描上界：取最大档位；**未配置档位时用规则键的代码兜底**，不再写第二份字面量
         * （否则"配置中心兜底值"与"服务里的兜底值"会各自漂移，而种子一致性门禁只盯前者）。
         *
         * <p>兜底文本必然可解析为整数：种子与代码兜底的一致性由
         * {@code RuleConfigServiceImplTest#sqlSeed_matchesCodeFallbacks} 守卫。
         */
        int maxDays() {
            return thresholds.isEmpty()
                    ? Integer.parseInt(cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys.fallback(
                            cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys.PERSONNEL_CERT_EXPIRING_WINDOW_DAYS))
                    : thresholds.get(thresholds.size() - 1);
        }
    }
}
