package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationAssetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationComplianceReviewDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationAssetMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationComplianceReviewMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationPerformanceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStaffConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStandardMapper;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStandardDO;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.QualificationReviewResultEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationReviewTypeEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准库/达标核查服务实现（只追加评审 · 结论可复算）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十五（要素聚合口径、15-6 只追加·防抵赖、快照类比自查双快照）；
 * 《招投标模块开发规范》§4.5（写操作事务）。
 * <p>设计遵循/针对：① 核查前先落时点快照（人员分角色/业绩/资产审计口径），结论差异 diff_items 留证——事后
 * 任何人无法篡改“当时为何判 PASS/FAIL”；② 判定引擎只消费标准 req JSON 中引擎支持的键（reg_builder/mid_title/
 * skilled/net_asset_min/recent_general_min/platform_verified_min），缺键即跳过该维度，扩展值域不动 DDL（§17.6 码表口径）；
 * ③ 注册建造师缺口判 FAIL（资质标准硬门槛），四库未核验业绩判 RISK（线上核验缺口可补材料）——口径写入 diff 供复核。
 */
@Slf4j
@Service
public class EnterpriseQualificationComplianceServiceImpl implements EnterpriseQualificationComplianceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    @Resource
    private QualificationStandardMapper qualificationStandardMapper;
    @Resource
    private QualificationStaffConfigMapper qualificationStaffConfigMapper;
    @Resource
    private QualificationPerformanceMapper qualificationPerformanceMapper;
    @Resource
    private QualificationAssetMapper qualificationAssetMapper;
    @Resource
    private QualificationComplianceReviewMapper qualificationComplianceReviewMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStandard(String qualSerial, String qualCategory, String qualGrade, String stdVersion,
                               String reqStaff, String reqAssets, String reqPerformance, String reqEquipment,
                               LocalDate effectiveFrom, LocalDate effectiveTo, String sourceRef) {
        if (qualificationStandardMapper.selectByScopeVersion(qualSerial, qualCategory, qualGrade, stdVersion) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_STANDARD_VERSION_DUPLICATE);
        }
        QualificationStandardDO std = new QualificationStandardDO();
        std.setQualSerial(qualSerial);
        std.setQualCategory(qualCategory);
        std.setQualGrade(qualGrade);
        std.setStdVersion(stdVersion);
        std.setReqStaff(reqStaff);
        std.setReqAssets(reqAssets);
        std.setReqPerformance(reqPerformance);
        std.setReqEquipment(reqEquipment);
        std.setEffectiveFrom(effectiveFrom == null ? LocalDate.now() : effectiveFrom);
        std.setEffectiveTo(effectiveTo);
        std.setSourceRef(sourceRef);
        qualificationStandardMapper.insert(std);
        log.info("[qual-standard][create] serial={} grade={} version={} id={}", qualSerial, qualGrade, stdVersion, std.getId());
        return std.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long runComplianceReview(Long qualId, QualificationReviewTypeEnum reviewType, Long reviewer,
                                    Long linkedApplyId) {
        EnterpriseQualificationDO qual = enterpriseQualificationMapper.selectById(qualId);
        if (qual == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_NOT_EXISTS);
        }
        LocalDate asOf = LocalDate.now();
        QualificationStandardDO std = qualificationStandardMapper.selectCurrentStandard(
                qual.getQualSerial(), qual.getQualCategory(), qual.getQualGrade(), asOf);
        if (std == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_STANDARD_NOT_FOUND);
        }
        // 1) 要素聚合：人员分角色有效在册计数（qual 维度）/业绩（qual 归属，§17.6）/净资产（ent 最新审计年度）
        Map<String, Number> staffCounts = new HashMap<>();
        for (Map<String, Object> row : qualificationStaffConfigMapper.selectActiveCountByRole(qualId, asOf)) {
            Object roleKey = row.get("role_key");
            Object cnt = row.get("cnt");
            if (roleKey != null && cnt instanceof Number n) {
                staffCounts.put(String.valueOf(roleKey), n);
            }
        }
        long recentPerf = qualificationPerformanceMapper
                .countRecentGeneralByQual(qualId, asOf.minusYears(3));
        long platformVerified = qualificationPerformanceMapper.countPlatformVerifiedByQual(qualId);
        BigDecimal netAsset = qualificationAssetMapper.sumLatestNetAsset(qual.getEntId());

        // 2) 对照标准 req JSON 逐项判定（仅消费引擎支持键，缺键跳过）
        List<String> diffs = new ArrayList<>();
        Map<String, Object> staffReq = parseJsonMap(std.getReqStaff());
        // 人员 req 键与 staff_role 枚举名一致（REGISTERED_BUILDER/MID_TITLE/SKILLED_WORKER…），口径见类注释
        failIfMissing(staffReq, "REGISTERED_BUILDER", staffCounts, "注册建造师", diffs);
        riskIfMissing(staffReq, "MID_TITLE", staffCounts, "中级职称", diffs);
        riskIfMissing(staffReq, "SKILLED_WORKER", staffCounts, "技工", diffs);
        Map<String, Object> assetReq = parseJsonMap(std.getReqAssets());
        if (assetReq.get("net_asset_min") instanceof Number min) {
            if (netAsset.compareTo(new BigDecimal(min.toString())) < 0) {
                diffs.add("净资产未达：" + min + "（现有 " + netAsset + "）");
            }
        }
        Map<String, Object> perfReq = parseJsonMap(std.getReqPerformance());
        Number recentMin = perfReq.get("recent_general_min") instanceof Number n ? n : null;
        if (recentMin != null && recentPerf < recentMin.longValue()) {
            diffs.add("近三年总承包业绩不足：需 " + recentMin.longValue() + " 项（现有 " + recentPerf + "）");
        }
        Number verifiedMin = perfReq.get("platform_verified_min") instanceof Number n ? n : null;
        if (verifiedMin != null && platformVerified < verifiedMin.longValue()) {
            diffs.add("四库一平台已核验业绩不足：需 " + verifiedMin.longValue() + " 项（现有 " + platformVerified + "，可线上补核验）");
        }
        // 3) 快照 + 三档结论：注册建造师/净资产/近三年业绩为硬门槛 → 缺口 FAIL；其余 RISK；无差异 PASS
        boolean hard = hasHardGap(diffs);
        String verdict = diffs.isEmpty() ? QualificationReviewResultEnum.PASS.name()
                : (hard ? QualificationReviewResultEnum.FAIL.name() : QualificationReviewResultEnum.RISK.name());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("std_version", std.getStdVersion());
        snapshot.put("reviewed_at", asOf.toString());
        snapshot.put("staff_active", staffCounts);
        snapshot.put("general_perf_recent_3y", recentPerf);
        snapshot.put("perf_platform_verified", platformVerified);
        snapshot.put("net_asset_latest_audit", netAsset);

        QualificationComplianceReviewDO review = new QualificationComplianceReviewDO();
        review.setEntId(qual.getEntId());
        review.setQualId(qualId);
        review.setStdVersion(std.getStdVersion());
        review.setReviewType(reviewType == null ? QualificationReviewTypeEnum.SELF.name() : reviewType.name());
        review.setElementSnapshot(writeJson(snapshot));
        review.setReviewResult(verdict);
        review.setDiffItems(writeJson(diffs));
        review.setReviewer(reviewer);
        review.setReviewedAt(java.time.LocalDateTime.now());
        review.setLinkedApplyId(linkedApplyId);
        qualificationComplianceReviewMapper.insert(review);
        log.info("[qual-review][run] qual={} verdict={} diffs={} reviewId={}", qualId, verdict, diffs.size(), review.getId());
        return review.getId();
    }

    // ========== 判定口径 ==========

    /** 硬门槛判定：注册建造师/净资产/近三年业绩缺口 → FAIL（"缺口"关键词入 diff）。 */
    private boolean hasHardGap(List<String> diffs) {
        return diffs.stream().anyMatch(s -> s.startsWith("注册建造师") || s.startsWith("净资产")
                || s.startsWith("近三年总承包业绩"));
    }

    private void failIfMissing(Map<String, Object> req, String key, Map<String, Number> actual,
                               String label, List<String> diffs) {
        if (!(req.get(key) instanceof Number need)) {
            return;
        }
        long have = actual.containsKey(key) ? actual.get(key).longValue() : 0;
        if (have < need.longValue()) {
            diffs.add(label + "不足：需 " + need.longValue() + " 名（有效在册 " + have + "）");
        }
    }

    private void riskIfMissing(Map<String, Object> req, String key, Map<String, Number> actual,
                               String label, List<String> diffs) {
        if (!(req.get(key) instanceof Number need)) {
            return;
        }
        long have = actual.containsKey(key) ? actual.get(key).longValue() : 0;
        if (have < need.longValue()) {
            diffs.add(label + "不足：需 " + need.longValue() + " 名（有效在册 " + have + "，风险项）");
        }
    }

    // ========== JSON 工具 ==========

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            log.warn("[qual-review] req JSON 解析失败，按空处理：{}", json);
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
