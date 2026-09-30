package cn.iocoder.txgy.module.bidding.service.safety;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.KeyPostAssignmentCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.KeyPostAssignmentRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.KeyPostChangeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.PersonRiskRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.RiskSnapshotRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyGraceRegisterReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyPermitRiskRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelJobChangeLogDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelMultiPostCheckDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.ProjectKeyPostAssignmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.SafetyPermitRiskSnapshotDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelJobChangeLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelMultiPostCheckMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.ProjectKeyPostAssignmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.SafetyPermitRiskSnapshotMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.KeyPostEnum;
import cn.iocoder.txgy.module.bidding.enums.PersonRiskFlagEnum;
import cn.iocoder.txgy.module.bidding.enums.RiskLevelEnum;
import cn.iocoder.txgy.module.bidding.enums.SafetyGraceReasonEnum;
import cn.iocoder.txgy.module.bidding.enums.SafetyRiskFlagEnum;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.txgy.module.bidding.service.project.ProjectService;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 安许 A/B/C 联动风险服务实现（判定顺序 + 宽限/豁免 + 快照哈希链 + 人员风险 + 关键岗位 + 多岗校验）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.2（判定顺序与配置键）、§1.3（Job/接口）、
 * §1.4（拦截点）、§8.2（哈希链审计）；用户业务口径（黄/红/失效/频繁 + 180 天宽限 + 劳务分包豁免 +
 * 企业级/人员级分离 + 关键岗位频繁变更 + 多岗持证）。
 * <p>设计遵循/针对：① 判定顺序固定为"作废 → 全 0 → 任一为 0（豁免除外）→ 低于配备 → 宽限降档 → 频繁 → 人员标签"，
 * 与 06 文档 §四-1 的裁决一致（风险与非日期型失效必须排在日期判断之前）；
 * ② **一切阈值取自规则配置中心**（required/exempt/grace/frequent/block/level 映射），本类不含业务常量；
 * ③ 有效期与状态判断沿用既有 cert_status 口径，本服务只负责"人员配备"这一维度，不重复实现到期逻辑；
 * ④ 每次评估落快照（含 hit_rules 与 rule_version）并写哈希链，判定过程可复算、不可篡改；
 * ⑤ dryRun 只算不写，供上线核对与人工排查使用。
 * <p>业务目标：三类人员不达标可被及时发现、按配置口径处置，并在招投标/施工许可/现场检查三处保持一致。
 */
@Slf4j
@Service
public class SafetyPermitRiskServiceImpl implements SafetyPermitRiskService {

    /** 安许三类人员的证书类型 code（personnel_certificate.cert_type）。 */
    private static final String SAFETY_ASSESS_CERT = "SAFETY_ASSESS_CERT";
    /** 劳务分包企业识别关键词：企业类型字段尚未落地前，以资质类别文本匹配（见 plan §八 待接入项）。 */
    private static final String LABOR_SUBCONTRACT_KEYWORD = "劳务";

    /**
     * 规则读取审计的业务场景标记（写入 bidding_rule_config_audit.biz_scene）。
     *
     * <p>规范出处：《电子资质七大模块技术方案.md》§8.2（审计与追溯）。
     * <p>为什么在这些读取点写审计：A/B/C 风险判定与人员风险判定都是**会引发争议的对外结论**
     * （投标被判定风险、人员被判异常），必须能回放"当时用的是哪一版阈值、来自租户覆盖还是平台默认"。
     * 其余读取（前端配置页展示等）不写，避免把热路径的一次内存读变成一次 DB 写。
     */
    private static final String BIZ_SCENE_SAFETY_RISK_EVAL = "SAFETY_RISK_EVAL";
    /** 矩阵的缺省项键名（**结构约定**，不是业务取值：业务取值一律来自配置中心）。 */
    private static final String MULTI_POST_CERT_SUBTYPE_DEFAULT_ITEM = "DEFAULT";

    private static final String BIZ_SCENE_PERSONNEL_RISK = "PERSONNEL_RISK";
    private static final String BIZ_SCENE_PROJECT_KEY_POST_CHANGE = "PROJECT_KEY_POST_CHANGE";

    /** 读取规则并留读取审计（判定点专用；写入失败不影响取值，见 RuleConfigService#resolveWithAudit）。 */
    private RuleConfigItemRespVO audited(String ruleKey, String bizScene) {
        return ruleConfigService.resolveWithAudit(ruleKey, bizScene);
    }

    private int auditedInt(String ruleKey, String bizScene) {
        Object value = audited(ruleKey, bizScene).getValue();
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID, ruleKey);
    }

    private boolean auditedBool(String ruleKey, String bizScene) {
        Object value = audited(ruleKey, bizScene).getValue();
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID, ruleKey);
    }

    @Resource
    private QualificationSafetyPermitMapper safetyPermitMapper;
    @Resource
    private PersonnelCertificateMapper personnelCertificateMapper;
    @Resource
    private PersonnelProfileMapper personnelProfileMapper;
    @Resource
    private PersonnelJobChangeLogMapper jobChangeLogMapper;
    @Resource
    private ProjectKeyPostAssignmentMapper keyPostAssignmentMapper;

    /** 项目主档校验（项目不存在时由项目域抛 PROJECT_NOT_EXISTS，保持单一事实来源）。 */
    @Resource
    private ProjectService projectService;
    @Resource
    private PersonnelMultiPostCheckMapper multiPostCheckMapper;
    @Resource
    private SafetyPermitRiskSnapshotMapper snapshotMapper;
    @Resource
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    @Resource
    private RuleConfigService ruleConfigService;
    @Resource
    private SafetyRiskItemService safetyRiskItemService;

    // ========== 读取 ==========

    @Override
    public SafetyPermitRiskRespVO getRisk(Long entId, LocalDate asOf) {
        QualificationSafetyPermitDO permit = selectPermitByEnt(entId);
        if (permit == null) {
            // 未建安许证档：返回空风险（业务侧视为不阻断，由"材料完备性"另行提示缺件）
            SafetyPermitRiskRespVO empty = new SafetyPermitRiskRespVO();
            empty.setEntId(entId);
            empty.setACount(0);
            empty.setBCount(0);
            empty.setCCount(0);
            empty.setGraceApplied(Boolean.FALSE);
            empty.setBlocked(Boolean.FALSE);
            return empty;
        }
        return toResp(permit);
    }

    @Override
    public PageResult<RiskSnapshotRespVO> pageSnapshot(Long permitId, Integer pageNo, Integer pageSize) {
        Page<SafetyPermitRiskSnapshotDO> page = snapshotMapper.selectPage(
                new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize),
                new LambdaQueryWrapper<SafetyPermitRiskSnapshotDO>()
                        .eq(SafetyPermitRiskSnapshotDO::getPermitId, permitId)
                        .orderByDesc(SafetyPermitRiskSnapshotDO::getEvaluatedAt)
                        .orderByDesc(SafetyPermitRiskSnapshotDO::getId));
        List<RiskSnapshotRespVO> list = new ArrayList<>();
        for (SafetyPermitRiskSnapshotDO row : page.getRecords()) {
            RiskSnapshotRespVO vo = new RiskSnapshotRespVO();
            vo.setId(row.getId());
            vo.setRiskFlag(row.getRiskFlag());
            vo.setPrevRiskFlag(row.getPrevRiskFlag());
            vo.setRiskLevel(row.getRiskLevel());
            vo.setACount(row.getACount());
            vo.setBCount(row.getBCount());
            vo.setCCount(row.getCCount());
            vo.setGraceApplied(row.getGraceApplied());
            vo.setHitRulesJson(row.getHitRulesJson());
            vo.setMinRequiredJson(row.getMinRequiredJson());
            vo.setRuleVersion(row.getRuleVersion());
            vo.setBatchNo(row.getBatchNo());
            vo.setEvaluatedAt(row.getEvaluatedAt());
            vo.setRecordHash(row.getRecordHash());
            vo.setPrevHash(row.getPrevHash());
            list.add(vo);
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public PersonRiskRespVO getPersonRisk(Long personId) {
        PersonnelProfileDO person = personnelProfileMapper.selectById(personId);
        PersonRiskRespVO vo = new PersonRiskRespVO();
        vo.setPersonId(personId);
        if (person == null) {
            vo.setPersonRiskFlag(PersonRiskFlagEnum.NORMAL.name());
            vo.setJobChangeCount12m(0);
            vo.setAbnormal(Boolean.FALSE);
            return vo;
        }
        String flag = person.getPersonRiskFlag() == null ? PersonRiskFlagEnum.NORMAL.name() : person.getPersonRiskFlag();
        vo.setPersonRiskFlag(flag);
        vo.setJobChangeCount12m(person.getJobChangeCount12m() == null ? 0 : person.getJobChangeCount12m());
        vo.setJobChangeWindowStart(person.getJobChangeWindowStart());
        vo.setPersonRiskEvaluatedAt(person.getPersonRiskEvaluatedAt());
        vo.setAbnormal(!PersonRiskFlagEnum.NORMAL.name().equals(flag));
        return vo;
    }

    // ========== 评估 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SafetyPermitRiskRespVO evaluate(Long permitId, LocalDate asOf, boolean dryRun) {
        QualificationSafetyPermitDO permit = safetyPermitMapper.selectById(permitId);
        if (permit == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_NOT_EXISTS);
        }
        return evaluateOne(permit, asOf == null ? LocalDate.now() : asOf, dryRun, null);
    }

    @Override
    public int evaluateAll(LocalDate asOf, boolean dryRun) {
        LocalDate base = asOf == null ? LocalDate.now() : asOf;
        String batchNo = "RISK-" + base.format(DateTimeFormatter.BASIC_ISO_DATE);
        List<QualificationSafetyPermitDO> permits = safetyPermitMapper.selectList(
                new LambdaQueryWrapper<QualificationSafetyPermitDO>());
        int evaluated = 0;
        for (QualificationSafetyPermitDO permit : permits) {
            try {
                evaluateOne(permit, base, dryRun, batchNo);
                evaluated++;
            } catch (Exception ex) {
                // 单证失败不中断批次：人员数据异常不应导致整批风险状态停更（否则全租户都看不到最新风险）
                log.error("[safety-risk][evaluate] permitId={} 评估失败，已跳过：{}", permit.getId(), ex.getMessage());
            }
        }
        log.info("[safety-risk][evaluateAll] asOf={} dryRun={} evaluated={}", base, dryRun, evaluated);
        return evaluated;
    }

    /** 单证评估主流程（供单证重算与全量 Job 共用，保证两条入口口径完全一致）。 */
    private SafetyPermitRiskRespVO evaluateOne(QualificationSafetyPermitDO permit, LocalDate asOf, boolean dryRun,
                                               String batchNo) {
        Map<String, Integer> required = stringIntMap(RuleConfigKeys.SAFETY_ABC_REQUIRED);
        List<String> roles = stringList(RuleConfigKeys.SAFETY_ABC_ROLES);
        Map<String, Integer> counts = resolveCounts(permit.getEntId(), asOf);
        Set<String> exemptRoles = resolveExemptRoles(permit.getEntId());
        boolean graceApplied = isInGrace(permit);
        long warnings = snapshotMapper.countWarnings(permit.getId(),
                LocalDateTime.now().minusDays(auditedInt(RuleConfigKeys.SAFETY_ABC_FREQUENT_WINDOW_DAYS,
                        BIZ_SCENE_SAFETY_RISK_EVAL)));
        int frequentThreshold = auditedInt(RuleConfigKeys.SAFETY_ABC_FREQUENT_ALERT_THRESHOLD,
                BIZ_SCENE_SAFETY_RISK_EVAL);
        int invalidGraceDays = auditedInt(RuleConfigKeys.SAFETY_ABC_INVALID_GRACE_DAYS, BIZ_SCENE_SAFETY_RISK_EVAL);
        LocalDateTime firstAllZeroAt = snapshotMapper.selectFirstAllZeroAt(permit.getId());

        Map<String, Object> hitRules = new LinkedHashMap<>();
        String flag = decide(permit, roles, required, counts, exemptRoles, graceApplied, warnings, frequentThreshold,
                invalidGraceDays, firstAllZeroAt, hitRules);
        String level = levelOf(flag);
        String prevFlag = permit.getRiskFlag();

        SafetyPermitRiskRespVO resp = new SafetyPermitRiskRespVO();
        resp.setPermitId(permit.getId());
        resp.setEntId(permit.getEntId());
        resp.setRiskFlag(flag);
        resp.setRiskLevel(level);
        resp.setACount(counts.getOrDefault("A", 0));
        resp.setBCount(counts.getOrDefault("B", 0));
        resp.setCCount(counts.getOrDefault("C", 0));
        resp.setGraceExpireAt(permit.getGraceExpireAt());
        resp.setGraceReason(permit.getGraceReason());
        resp.setGraceApplied(graceApplied);
        resp.setBlocked(isBlocking(flag));
        resp.setHitRules(toJson(hitRules));

        if (dryRun) {
            return resp;
        }
        // 落快照（含可复算依据）+ 回写主档；仅在档位变化时打日志，避免刷屏
        SafetyPermitRiskSnapshotDO snapshot = new SafetyPermitRiskSnapshotDO();
        snapshot.setId(IdWorker.getId());
        snapshot.setPermitId(permit.getId());
        snapshot.setEntId(permit.getEntId());
        snapshot.setRiskFlag(flag);
        snapshot.setPrevRiskFlag(prevFlag);
        snapshot.setRiskLevel(level);
        snapshot.setACount(counts.getOrDefault("A", 0));
        snapshot.setBCount(counts.getOrDefault("B", 0));
        snapshot.setCCount(counts.getOrDefault("C", 0));
        snapshot.setMinRequiredJson(toJson(required));
        snapshot.setGraceApplied(graceApplied);
        snapshot.setHitRulesJson(resp.getHitRules());
        snapshot.setRuleVersion(String.valueOf(
                audited(RuleConfigKeys.SAFETY_ABC_REQUIRED, BIZ_SCENE_SAFETY_RISK_EVAL).getVersionNo()));
        snapshot.setBatchNo(batchNo);
        snapshot.setEvaluatedAt(LocalDateTime.now());
        snapshot.setRecordHash(sha256(snapshot));
        SafetyPermitRiskSnapshotDO last = snapshotMapper.selectLatest(permit.getId());
        snapshot.setPrevHash(last == null ? null : last.getRecordHash());
        snapshotMapper.insert(snapshot);

        permit.setRiskFlag(flag);
        permit.setRiskLevel(level);
        permit.setRiskEvaluatedAt(LocalDateTime.now());
        permit.setFrequentAlertCount((int) warnings);
        permit.setRuleVersion(snapshot.getRuleVersion());
        safetyPermitMapper.updateById(permit);

        // 契约 Q18/Q19（2026-09-28）：风险条目工单同步——非空档新开/点亮，回 NORMAL 自动闭环
        safetyRiskItemService.syncFromEvaluation(permit, level, flag, "安许证 " + permit.getPermitNo());

        if (!Objects.equals(prevFlag, flag)) {
            log.warn("[safety-risk][changed] permitId={} entId={} {} → {} (A={} B={} C={})",
                    permit.getId(), permit.getEntId(), prevFlag, flag,
                    counts.getOrDefault("A", 0), counts.getOrDefault("B", 0), counts.getOrDefault("C", 0));
        }
        resp.setRiskEvaluatedAt(permit.getRiskEvaluatedAt());
        return resp;
    }

    /**
     * 判定顺序（与方案 §1.2 / 06 文档 §四-1 一致）。
     *
     * <p>顺序不可调换的原因：宽限只能"降档"不能"赦免"——若先判宽限再看人数，
     * 三类全为 0 的企业也会被降成红色而看不到"失效"这一最严重结论。
     */
    private String decide(QualificationSafetyPermitDO permit, List<String> roles, Map<String, Integer> required,
                          Map<String, Integer> counts, Set<String> exemptRoles, boolean graceApplied, long warnings,
                          int frequentThreshold, int invalidGraceDays, LocalDateTime firstAllZeroAt,
                          Map<String, Object> hitRules) {
        // 1) 主证件已作废/吊销 → INVALID（行政结论优先于人数统计）
        if ("CANCELLED".equals(permit.getPermitStatus()) || "REVOKED".equals(permit.getPermitStatus())) {
            hitRules.put("permitStatus", permit.getPermitStatus());
            return SafetyRiskFlagEnum.INVALID.name();
        }
        int total = roles.stream().mapToInt(r -> counts.getOrDefault(r, 0)).sum();
        // 2) 三类全为 0：宽限期内仍按红色（限期整改），逾期列失效
        if (total == 0) {
            hitRules.put("allZeroSince", String.valueOf(firstAllZeroAt));
            boolean expired = firstAllZeroAt != null
                    && firstAllZeroAt.plusDays(invalidGraceDays).isBefore(LocalDateTime.now());
            if (expired) {
                hitRules.put("invalidReason", "三类全为 0 且超过整改宽限 " + invalidGraceDays + " 天");
                return SafetyRiskFlagEnum.INVALID.name();
            }
            hitRules.put("invalidGraceDays", invalidGraceDays);
            return SafetyRiskFlagEnum.RED.name();
        }
        // 3) 任一 required 类为 0（豁免角色除外）→ RED
        String zeroRole = null;
        for (String role : roles) {
            if (counts.getOrDefault(role, 0) == 0 && !exemptRoles.contains(role)) {
                zeroRole = role;
                break;
            }
        }
        if (zeroRole != null) {
            hitRules.put("zeroRole", zeroRole);
            return SafetyRiskFlagEnum.RED.name();
        }
        // 4) 任一类低于配备要求 → YELLOW
        String belowRole = null;
        for (String role : roles) {
            int need = required.getOrDefault(role, 0);
            if (counts.getOrDefault(role, 0) < need && !(exemptRoles.contains(role) && counts.getOrDefault(role, 0) == 0)) {
                belowRole = role + "(" + counts.getOrDefault(role, 0) + "/" + need + ")";
                break;
            }
        }
        if (belowRole != null) {
            hitRules.put("belowRole", belowRole);
            // 5) 宽限期内降一档：YELLOW → NORMAL（不达标但给了整改时间，不再双罚）
            if (graceApplied) {
                hitRules.put("graceApplied", true);
                return SafetyRiskFlagEnum.downgrade(SafetyRiskFlagEnum.YELLOW.name());
            }
            return SafetyRiskFlagEnum.YELLOW.name();
        }
        // 6) 窗口内预警次数达阈值 → FREQUENT（趋势性标签，不改"当下合格"的事实）
        if (warnings >= frequentThreshold) {
            hitRules.put("warnings", warnings);
            hitRules.put("frequentThreshold", frequentThreshold);
            return SafetyRiskFlagEnum.FREQUENT.name();
        }
        return SafetyRiskFlagEnum.NORMAL.name();
    }

    // ========== 人员级风险 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int evaluatePersonnelRisk(LocalDate asOf) {
        LocalDate base = asOf == null ? LocalDate.now() : asOf;
        int threshold = auditedInt(RuleConfigKeys.PERSONNEL_JOB_CHANGE_THRESHOLD, BIZ_SCENE_PERSONNEL_RISK);
        int windowDays = auditedInt(RuleConfigKeys.PERSONNEL_JOB_CHANGE_WINDOW_DAYS, BIZ_SCENE_PERSONNEL_RISK);
        LocalDate windowStart = base.minusDays(windowDays);
        List<PersonnelProfileDO> persons = personnelProfileMapper.selectList(new LambdaQueryWrapper<>());
        int abnormal = 0;
        for (PersonnelProfileDO person : persons) {
            long changes = jobChangeLogMapper.countChanges(person.getId(), windowStart);
            String flag = changes >= threshold ? PersonRiskFlagEnum.ABNORMAL.name() : PersonRiskFlagEnum.NORMAL.name();
            person.setJobChangeCount12m((int) changes);
            person.setJobChangeWindowStart(jobChangeLogMapper.selectEarliestChangeDate(person.getId(), windowStart));
            person.setPersonRiskFlag(flag);
            person.setPersonRiskEvaluatedAt(LocalDateTime.now());
            personnelProfileMapper.updateById(person);
            if (PersonRiskFlagEnum.ABNORMAL.name().equals(flag)) {
                abnormal++;
                log.warn("[person-risk][abnormal] personId={} 窗口内更换受聘企业 {} 次（阈值 {}）",
                        person.getId(), changes, threshold);
            }
        }
        log.info("[person-risk][evaluateAll] asOf={} total={} abnormal={}", base, persons.size(), abnormal);
        return abnormal;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MultiPostCheckRespVO checkMultiPost(MultiPostCheckReqVO reqVO) {
        if (Objects.equals(reqVO.getPrimaryPost(), reqVO.getSecondaryPost())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.MULTI_POST_SAME_POST);
        }
        if (!KeyPostEnum.isValid(reqVO.getPrimaryPost()) || !KeyPostEnum.isValid(reqVO.getSecondaryPost())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.KEY_POST_INVALID,
                    reqVO.getPrimaryPost() + "/" + reqVO.getSecondaryPost());
        }
        boolean requireCert = auditedBool(RuleConfigKeys.PERSONNEL_MULTI_POST_REQUIRE_CERT, BIZ_SCENE_PERSONNEL_RISK);
        MultiPostCheckRespVO resp = new MultiPostCheckRespVO();
        resp.setCheckedAt(LocalDateTime.now());
        resp.setRequiredCertType(SAFETY_ASSESS_CERT);
        resp.setRequiredCertSubType(secondarySubType(reqVO.getSecondaryPost()));
        boolean pass = true;
        String failReason = null;
        Long matchedCertId = null;
        if (requireCert) {
            // 判定依据：该员工名下"可用状态"的安许考核合格证中，是否存在兼任岗位要求的细分（安全员=C 类）
            // 按"人"取证书（selectUsableByType 是"按企业+类型"取，用它会把同事的证算到自己头上）
            List<cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO> certs =
                    personnelCertificateMapper.selectByPerson(reqVO.getPersonId());
            String needSubType = resp.getRequiredCertSubType();
            cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO matched =
                    certs == null ? null : certs.stream()
                            .filter(c -> SAFETY_ASSESS_CERT.equals(c.getCertType()))
                            // 口径与配证一致：仅 VALID/EXPIRING_SOON 视为可用（过期证不算持证）
                            .filter(c -> "VALID".equals(c.getCertStatus()) || "EXPIRING_SOON".equals(c.getCertStatus()))
                            .filter(c -> Objects.equals(needSubType, c.getCertSubType()))
                            .findFirst().orElse(null);
            pass = matched != null;
            matchedCertId = matched == null ? null : matched.getId();
            if (!pass) {
                failReason = "缺少兼任岗位要求的证书：" + KeyPostEnum.labelOf(reqVO.getSecondaryPost())
                        + "（需 " + needSubType + " 类安全生产考核合格证）";
            }
        }
        resp.setPass(pass);
        resp.setFailReason(failReason);
        resp.setMatchedCertId(matchedCertId);

        PersonnelMultiPostCheckDO record = new PersonnelMultiPostCheckDO();
        record.setId(IdWorker.getId());
        record.setPersonId(reqVO.getPersonId());
        record.setPrimaryPost(reqVO.getPrimaryPost());
        record.setSecondaryPost(reqVO.getSecondaryPost());
        record.setRequiredCertType(resp.getRequiredCertType());
        record.setRequiredCertSubType(resp.getRequiredCertSubType());
        record.setMatchedCertId(matchedCertId);
        record.setCheckResult(pass ? "PASS" : "FAIL");
        record.setFailReason(failReason);
        record.setCheckedAt(resp.getCheckedAt());
        record.setRuleVersion(String.valueOf(
                audited(RuleConfigKeys.PERSONNEL_MULTI_POST_REQUIRE_CERT, BIZ_SCENE_PERSONNEL_RISK).getVersionNo()));
        multiPostCheckMapper.insert(record);
        return resp;
    }

    // ========== 关键岗位变更 ==========

    @Override
    public KeyPostChangeRespVO getKeyPostChanges(Long projectId, Integer windowDays) {
        int days = windowDays == null || windowDays <= 0
                ? auditedInt(RuleConfigKeys.PROJECT_KEY_POST_CHANGE_WINDOW_DAYS, BIZ_SCENE_PROJECT_KEY_POST_CHANGE)
                : windowDays;
        int threshold = auditedInt(RuleConfigKeys.PROJECT_KEY_POST_CHANGE_THRESHOLD, BIZ_SCENE_PROJECT_KEY_POST_CHANGE);
        LocalDate windowStart = LocalDate.now().minusDays(days);
        List<ProjectKeyPostAssignmentDO> rows = keyPostAssignmentMapper.selectByProjectInWindow(projectId, windowStart);

        KeyPostChangeRespVO resp = new KeyPostChangeRespVO();
        resp.setProjectId(projectId);
        resp.setWindowDays(days);
        resp.setWindowStart(windowStart);
        Map<String, List<ProjectKeyPostAssignmentDO>> byPost = new LinkedHashMap<>();
        for (ProjectKeyPostAssignmentDO row : rows) {
            byPost.computeIfAbsent(row.getPostCode(), k -> new ArrayList<>()).add(row);
        }
        List<KeyPostChangeRespVO.PostChangeItem> items = new ArrayList<>();
        boolean anyExceeded = false;
        for (Map.Entry<String, List<ProjectKeyPostAssignmentDO>> entry : byPost.entrySet()) {
            KeyPostChangeRespVO.PostChangeItem item = new KeyPostChangeRespVO.PostChangeItem();
            item.setPostCode(entry.getKey());
            item.setPostLabel(KeyPostEnum.labelOf(entry.getKey()));
            // 窗口内任职记录条数 - 1 = 变更次数（第一条是初始任职，不算变更）
            int changeCount = Math.max(entry.getValue().size() - 1, 0);
            item.setChangeCount(changeCount);
            item.setThreshold(threshold);
            item.setExceeded(changeCount >= threshold);
            item.setPersonIds(entry.getValue().stream().map(ProjectKeyPostAssignmentDO::getPersonId).toList());
            item.setLastChangeDate(entry.getValue().get(entry.getValue().size() - 1).getStartAt());
            if (Boolean.TRUE.equals(item.getExceeded())) {
                anyExceeded = true;
                log.warn("[key-post][exceeded] projectId={} post={} 窗口 {} 天内变更 {} 次（阈值 {}）",
                        projectId, entry.getKey(), days, changeCount, threshold);
            }
            items.add(item);
        }
        resp.setItems(items);
        resp.setAnyExceeded(anyExceeded);
        return resp;
    }

    // ========== 关键岗位任职/变更登记（唯一写入口） ==========

    /**
     * 登记关键岗位任职/变更。
     *
     * <p>规范出处：用户业务口径"增加对同一项目关键岗位人员频繁变更的监控（防挂靠/管理失控）"；
     * 《电子资质七大模块技术方案.md》§1.1(e)/§1.3（变更统计与阈值）；《招投标模块开发规范》§3.1/3.2（分层与校验集中）。
     *
     * <p>为什么写成"一次登记、服务端闭合上任"而不是让调用方两步写：
     * 两步写必然存在中间态（两人同时在任或空档），而频繁变更统计正是按在任流水算的——
     * 中间态被统计读到就会误判。故本方法整体 {@code @Transactional}，失败即回滚，不留半截任期。
     *
     * <p>为什么用数据库唯一键（uk_post_seq）兜并发而不是先查后插：change_seq 是"读-算-写"，
     * 并发登记同一岗位时两个请求可能算出同一序号；先查后插在并发下必然漏，唯一键才是终局裁判，
     * 冲突时转成可读错误让前端重试（而不是抛 500）。
     *
     * <p>业务目标：换人当场完成留痕与重算，并立刻告诉前端"是否已达挂靠嫌疑阈值"——
     * 但**待办仍由 Job projectKeyPostChangeJob 统一产出**，此处只提示，避免同一嫌疑重复推送。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KeyPostAssignmentRespVO assignKeyPost(KeyPostAssignmentCreateReqVO reqVO) {
        // 1) 岗位必须来自枚举：统计口径依赖"同一岗位"的确定性判定，自由文本会让统计失真
        if (!KeyPostEnum.isValid(reqVO.getPostCode())) {
            // 复用既有多岗校验同款错误码（1_060_107_028）：同一条件只应有一个码，前端不必对同一根因分支两次
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.KEY_POST_INVALID, reqVO.getPostCode());
        }
        // 2) 项目/人员必须存在（各自领域负责判存在，避免这里再造一套口径）
        ProjectRespVO project = projectService.getProject(reqVO.getProjectId());
        if (personnelProfileMapper.selectById(reqVO.getPersonId()) == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_NOT_EXISTS);
        }
        LocalDate startAt = reqVO.getStartAt() == null ? LocalDate.now() : reqVO.getStartAt();

        // 3) 闭合上任任期（变更的实质）；日期倒退与"同一人重复登记"都要挡住
        ProjectKeyPostAssignmentDO current = keyPostAssignmentMapper.selectCurrent(reqVO.getProjectId(),
                reqVO.getPostCode());
        Long previousPersonId = null;
        LocalDate previousEndAt = null;
        if (current != null) {
            if (current.getStartAt() != null && startAt.isBefore(current.getStartAt())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_KEY_POST_CHANGE_DATE_INVALID,
                        startAt, current.getStartAt());
            }
            if (Objects.equals(current.getPersonId(), reqVO.getPersonId())) {
                // 重复登记会虚增变更次数，把续任/补录误判为频繁变更 → 直接拒绝
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_KEY_POST_SAME_PERSON);
            }
            current.setEndAt(startAt);
            if (keyPostAssignmentMapper.updateById(current) == 0) {
                // 影响 0 行=记录被他人改动/删除（CAS 语义，与人员域 PERSONNEL_DATA_MODIFIED 同范式）
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_KEY_POST_CONCURRENT_MODIFIED);
            }
            previousPersonId = current.getPersonId();
            previousEndAt = startAt;
        }

        // 4) 追加新任职流水（序号 = 当前最大 + 1；唯一键 uk_post_seq 兜住并发）
        ProjectKeyPostAssignmentDO row = new ProjectKeyPostAssignmentDO();
        row.setProjectId(reqVO.getProjectId());
        row.setTenderProjectCode(project.getProjectCode());
        row.setPostCode(reqVO.getPostCode());
        row.setPersonId(reqVO.getPersonId());
        row.setStartAt(startAt);
        row.setChangeSeq(keyPostAssignmentMapper.selectMaxSeq(reqVO.getProjectId(), reqVO.getPostCode()) + 1);
        row.setChangeReason(reqVO.getChangeReason());
        try {
            keyPostAssignmentMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.warn("[key-post][conflict] projectId={} post={} seq={} reason={}", reqVO.getProjectId(),
                    reqVO.getPostCode(), row.getChangeSeq(), e.getMessage());
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_KEY_POST_SEQ_CONFLICT);
        }

        // 5) 重算窗口内变更次数：与读接口**同一口径**（窗口内该岗位记录数 - 1，首条是初始任职）
        int days = auditedInt(RuleConfigKeys.PROJECT_KEY_POST_CHANGE_WINDOW_DAYS, BIZ_SCENE_PROJECT_KEY_POST_CHANGE);
        int threshold = auditedInt(RuleConfigKeys.PROJECT_KEY_POST_CHANGE_THRESHOLD, BIZ_SCENE_PROJECT_KEY_POST_CHANGE);
        LocalDate windowStart = LocalDate.now().minusDays(days);
        long countInWindow = keyPostAssignmentMapper.selectByProjectInWindow(reqVO.getProjectId(), windowStart)
                .stream().filter(item -> reqVO.getPostCode().equals(item.getPostCode())).count();
        int changeCount = (int) Math.max(countInWindow - 1, 0);

        KeyPostAssignmentRespVO resp = new KeyPostAssignmentRespVO();
        resp.setId(row.getId());
        resp.setProjectId(reqVO.getProjectId());
        resp.setPostCode(reqVO.getPostCode());
        resp.setPostLabel(KeyPostEnum.labelOf(reqVO.getPostCode()));
        resp.setPersonId(reqVO.getPersonId());
        resp.setStartAt(startAt);
        resp.setChangeSeq(row.getChangeSeq());
        resp.setPreviousPersonId(previousPersonId);
        resp.setPreviousEndAt(previousEndAt);
        resp.setWindowDays(days);
        resp.setWindowStart(windowStart);
        resp.setWindowChangeCount(changeCount);
        resp.setThreshold(threshold);
        resp.setExceeded(changeCount >= threshold);
        if (Boolean.TRUE.equals(resp.getExceeded())) {
            log.warn("[key-post][assigned-exceeded] projectId={} post={} 窗口 {} 天内变更 {} 次（阈值 {}）",
                    reqVO.getProjectId(), reqVO.getPostCode(), days, changeCount, threshold);
        }
        return resp;
    }

    // ========== 宽限 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerGrace(SafetyGraceRegisterReqVO reqVO) {
        QualificationSafetyPermitDO permit = reqVO.getPermitId() != null
                ? safetyPermitMapper.selectById(reqVO.getPermitId()) : selectPermitByEnt(reqVO.getEntId());
        if (permit == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_NOT_EXISTS);
        }
        // 原因白名单：宽限会成为"合法拖延"，必须限定在非主观过错的情形
        List<String> whitelist = stringList(RuleConfigKeys.SAFETY_ABC_GRACE_REASON_WHITELIST);
        if (!whitelist.contains(reqVO.getReason())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_GRACE_REASON_NOT_ALLOWED, reqVO.getReason());
        }
        if (!isValidGraceReason(reqVO.getReason())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_GRACE_REASON_NOT_ALLOWED, reqVO.getReason());
        }
        int maxDays = auditedInt(RuleConfigKeys.SAFETY_ABC_GRACE_LEGAL_PERSON_CHANGE_DAYS, BIZ_SCENE_SAFETY_RISK_EVAL);
        int days = reqVO.getDays() == null ? maxDays : reqVO.getDays();
        if (days <= 0 || days > maxDays) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_GRACE_DAYS_EXCEEDED, maxDays);
        }
        permit.setGraceExpireAt(LocalDateTime.now().plusDays(days));
        permit.setGraceReason(reqVO.getReason());
        permit.setGraceSourceRef(reqVO.getSourceRef());
        safetyPermitMapper.updateById(permit);
        log.warn("[safety-risk][grace] permitId={} entId={} reason={} days={} ref={}",
                permit.getId(), permit.getEntId(), reqVO.getReason(), days, reqVO.getSourceRef());
    }

    private boolean isValidGraceReason(String reason) {
        try {
            SafetyGraceReasonEnum.valueOf(reason);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    // ========== 内部工具 ==========

    private QualificationSafetyPermitDO selectPermitByEnt(Long entId) {
        if (entId == null) {
            return null;
        }
        return safetyPermitMapper.selectOne(new LambdaQueryWrapper<QualificationSafetyPermitDO>()
                .eq(QualificationSafetyPermitDO::getEntId, entId)
                .orderByDesc(QualificationSafetyPermitDO::getId)
                .last("LIMIT 1"));
    }

    /** 按证书细分统计"可用持证人数"（仅 VALID/EXPIRING_SOON 计入，口径与员工证书服务一致）。 */
    private Map<String, Integer> resolveCounts(Long entId, LocalDate asOf) {
        // 注意：mapper 返回的是 List<Map>（SELECT cert_sub_type AS sub_type, COUNT(*) AS cnt），
        // 这里换算成角色→人数，避免上层到处处理 Map 的键名细节
        List<Map<String, Object>> rows = personnelCertificateMapper.countUsableBySubType(entId, SAFETY_ASSESS_CERT, asOf);
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (rows == null) {
            return counts;
        }
        for (Map<String, Object> row : rows) {
            Object subType = row.get("sub_type");
            Object cnt = row.get("cnt");
            if (subType == null || cnt == null) {
                continue;
            }
            counts.put(String.valueOf(subType), Integer.valueOf(String.valueOf(cnt)));
        }
        return counts;
    }

    /**
     * 解析豁免角色（按企业类型）。
     *
     * <p>为什么用资质类别关键词而不用专门的企业类型字段：当前企业主档尚无"企业类型"列，
     * 而劳务分包判定直接影响"是否触发红色"这一硬结论，先用资质类别文本匹配兜住业务；
     * 待企业类型字段落地后改为直接读列（已登记为 plan §八 的待接入项）。
     */
    private Set<String> resolveExemptRoles(Long entId) {
        if (entId == null) {
            return Set.of();
        }
        Map<String, Object> exemptMatrix = objectMap(RuleConfigKeys.SAFETY_ABC_EXEMPT_ENT_TYPES);
        if (exemptMatrix.isEmpty()) {
            return Set.of();
        }
        List<EnterpriseQualificationDO> quals = enterpriseQualificationMapper.selectList(
                new LambdaQueryWrapper<EnterpriseQualificationDO>()
                        .eq(EnterpriseQualificationDO::getEntId, entId));
        boolean laborSubcontract = quals.stream().anyMatch(q -> q.getQualCategory() != null
                && q.getQualCategory().contains(LABOR_SUBCONTRACT_KEYWORD));
        if (!laborSubcontract) {
            return Set.of();
        }
        Object roles = exemptMatrix.get("LABOR_SUBCONTRACT");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    private boolean isInGrace(QualificationSafetyPermitDO permit) {
        if (permit.getGraceExpireAt() == null) {
            return false;
        }
        List<String> whitelist = stringList(RuleConfigKeys.SAFETY_ABC_GRACE_REASON_WHITELIST);
        boolean reasonOk = permit.getGraceReason() != null && whitelist.contains(permit.getGraceReason());
        return reasonOk && permit.getGraceExpireAt().isAfter(LocalDateTime.now());
    }

    /** 是否阻断业务（阻断档位取自配置，默认 INVALID+RED）。 */
    private boolean isBlocking(String flag) {
        List<String> blockFlags = stringList(RuleConfigKeys.SAFETY_ABC_BLOCK_FLAGS);
        if (blockFlags.isEmpty()) {
            return SafetyRiskFlagEnum.isBlockingByDefault(flag);
        }
        return blockFlags.contains(flag);
    }

    private String levelOf(String flag) {
        Map<String, Object> mapping = objectMap(RuleConfigKeys.RISK_LEVEL_MAPPING);
        Object level = mapping.get(flag);
        return RiskLevelEnum.parseOrDefaultMajor(level == null ? null : String.valueOf(level)).name();
    }

    /**
     * 兼任岗位 → 需要的安许考核合格证细分（A/B/C 类）。
     *
     * <p>规范出处：用户业务口径"兼任两个及以上岗位的，必须持另一岗位的考核合格证书"；
     * 用户硬性要求"所有口径可动态配置、严禁硬编码"；《电子资质七大模块技术方案.md》§8.1（阈值/口径集中受控）。
     * <p>为什么改成读配置：原实现是 {@code SAFETY_OFFICER.equals(post) ? "C" : "C"}——两个分支同值，
     * 三元表达式形同虚设，实际等于"所有岗位都要 C 类"写死在代码里；业务确认矩阵（如项目负责人需 B 类）时
     * 必须改代码发版，而配证校验直接决定投标能否被拦截，口径变更不应依赖发版。
     * <p>为什么不给"矩阵缺 DEFAULT"兜底：宁可**大声失败**（RULE_CONFIG_VALUE_FORMAT_INVALID），
     * 也不要静默用代码兜底值放过一次配证校验——校验结论错误比接口报错更难发现。
     */
    private String secondarySubType(String secondaryPost) {
        Map<String, String> mapping = stringMap(RuleConfigKeys.PERSONNEL_MULTI_POST_CERT_SUBTYPE_MAPPING,
                BIZ_SCENE_PERSONNEL_RISK);
        String subType = mapping.get(secondaryPost);
        if (subType == null || subType.isBlank()) {
            subType = mapping.get(MULTI_POST_CERT_SUBTYPE_DEFAULT_ITEM);
        }
        if (subType == null || subType.isBlank()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID,
                    RuleConfigKeys.PERSONNEL_MULTI_POST_CERT_SUBTYPE_MAPPING);
        }
        return subType;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(String ruleKey) {
        Object value = audited(ruleKey, BIZ_SCENE_SAFETY_RISK_EVAL).getValue();
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(String ruleKey) {
        // 三个 helper 只服务安许 A/B/C 风险判定（SAFETY_ABC_* 与 RISK_LEVEL_MAPPING），故场景固定为该判定。
        // 注意：审计调用必须**留在本方法内联**，不要抽成"带场景参数的重载"——
        // 读取审计门禁按 audited*(..., BIZ_SCENE_X) 统计各类判定的留证点，
        // 一旦场景常量改由参数传递，该类判定的留证点会被算成 0（门禁会红，这是它该有的行为）。
        Object value = audited(ruleKey, BIZ_SCENE_SAFETY_RISK_EVAL).getValue();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    /**
     * 读 JSON 对象为字符串映射（值一律按文本取，缺省项由调用方处理）。
     *
     * <p>为什么由调用方传场景、且**在本方法内联取审计票**：配证矩阵读取发生在多岗校验（PERSONNEL_RISK 场景），
     * 与安许 A/B/C 判定（SAFETY_RISK_EVAL）不是同一类结论；场景必须如实标注，
     * 否则争议时无法回答"这次配证校验用的是哪一版矩阵"。
     */
    private Map<String, String> stringMap(String ruleKey, String bizScene) {
        Map<String, String> result = new LinkedHashMap<>();
        asObjectMap(audited(ruleKey, bizScene).getValue())
                .forEach((k, v) -> result.put(k, v == null ? null : String.valueOf(v)));
        return result;
    }

    /** 把规则值按键值对解释（非对象的配置返回空表，由调用方决定是兜底还是报错）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObjectMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Map<String, Integer> stringIntMap(String ruleKey) {
        Map<String, Integer> result = new LinkedHashMap<>();
        objectMap(ruleKey).forEach((k, v) -> result.put(k, v == null ? 0 : Integer.valueOf(String.valueOf(v))));
        return result;
    }

    private String toJson(Object value) {
        return cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(value);
    }

    private SafetyPermitRiskRespVO toResp(QualificationSafetyPermitDO permit) {
        SafetyPermitRiskRespVO vo = new SafetyPermitRiskRespVO();
        vo.setPermitId(permit.getId());
        vo.setEntId(permit.getEntId());
        vo.setRiskFlag(permit.getRiskFlag());
        vo.setRiskLevel(permit.getRiskLevel());
        vo.setGraceExpireAt(permit.getGraceExpireAt());
        vo.setGraceReason(permit.getGraceReason());
        vo.setGraceApplied(isInGrace(permit));
        vo.setBlocked(permit.getRiskFlag() != null && isBlocking(permit.getRiskFlag()));
        vo.setRuleVersion(permit.getRuleVersion());
        vo.setRiskEvaluatedAt(permit.getRiskEvaluatedAt());
        return vo;
    }

    /** 快照哈希：SHA-256(关键字段 + prev_hash)，写入后即不可篡改（等保三级要求）。 */
    private String sha256(SafetyPermitRiskSnapshotDO snapshot) {
        String raw = String.join("|", String.valueOf(snapshot.getPermitId()), String.valueOf(snapshot.getRiskFlag()),
                String.valueOf(snapshot.getPrevRiskFlag()), String.valueOf(snapshot.getRiskLevel()),
                String.valueOf(snapshot.getACount()), String.valueOf(snapshot.getBCount()),
                String.valueOf(snapshot.getCCount()), String.valueOf(snapshot.getEvaluatedAt()),
                String.valueOf(snapshot.getRuleVersion()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            log.warn("[safety-risk][hash] 计算哈希失败: {}", ex.getMessage());
            return null;
        }
    }
}
