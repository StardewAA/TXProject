package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.EnterpriseMaterialCompletenessRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationPerformanceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationPerformanceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.enums.EnterpriseMaterialTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业材料完备性自查实现（分类逐项核验 → 三档结论）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 1/15-1/15-4/15-8（各材料的权威来源表）；
 * 《招投标模块开发规范》§4.3（Service 编排，本服务只读不写）、§4.4-4（结论用 code 表达）；
 * 《资质管理/06-电子证照设计材料对比与推进依据-2026-09-10.md》§二（陕西口径：非日期型失效、法定下限）。
 * <p>设计遵循/针对：① 材料类型 → 校验来源的映射固化在 EnterpriseMaterialTypeEnum（同枚举还带 mandatory 标记），
 * 避免散落的 if/else；② "有效"统一口径=记录存在且（无到期日 或 到期日 ≥ 基准日），
 * 与预警扫描口径一致；③ 临近到期阈值取规则键 `qual.material.expiring.window.days`（默认 60 天，
 * 原为硬编码 60；可配后运维可调提前期，判定结论可追溯到具体配置版本），
 * 进入该窗口即报 RISK，让企业有办理时间；④ 只读服务——完备性是可重复计算的结论，不落表（同 v3.3 表 15-6 只追加思路）。
 * <p>业务目标：投标前给出一份"材料清单 + 缺口 + 到期风险"，把被动被否决变成主动补齐。
 */
@Slf4j
@Service
public class EnterpriseMaterialServiceImpl implements EnterpriseMaterialService {

    // 注：原此处硬编码 EXPIRING_WINDOW_DAYS = 60（"临近到期即判 RISK"），已改为解析规则键
    // qual.material.expiring.window.days（默认值同为 60，故行为不变）。见 resolveExpiringWindowDays()。

    /**
     * 规则配置审计场景名：企业材料完备性自查。
     * 用于回答"这次自查的提前期是按哪版配置判的"——资格自查的结论会影响能否投标，必须可追溯。
     */
    private static final String BIZ_SCENE_MATERIAL_COMPLETENESS = "MATERIAL_COMPLETENESS";

    @Resource
    private RuleConfigService ruleConfigService;

    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    @Resource
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    @Resource
    private QualificationPerformanceMapper qualificationPerformanceMapper;

    /**
     * "临近到期"窗口（天）：经规则配置中心解析（审计可追溯）。
     *
     * <p>为什么走配置：这是**资格自查的判定口径**（材料有效期止距基准日 ≤ N 天即判 RISK），
     * 原实现硬编码 60 —— 运维想按行业/季节调整提前期只能改代码。默认值仍为 60，故接线行为不变。
     *
     * <p>解析失败即抛错，不静默回落：本键写入时已由 validateFormat 校验为 INT，
     * 损坏只可能来自绕过接口的写库，此时"按默认值继续算"会把"资格审查判成了错结论"藏起来。
     */
    private int resolveExpiringWindowDays() {
        String value = String.valueOf(ruleConfigService.resolveWithAudit(
                RuleConfigKeys.QUAL_MATERIAL_EXPIRING_WINDOW_DAYS, BIZ_SCENE_MATERIAL_COMPLETENESS).getValue());
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID,
                    RuleConfigKeys.QUAL_MATERIAL_EXPIRING_WINDOW_DAYS);
        }
    }

    @Override
    public EnterpriseMaterialCompletenessRespVO checkCompleteness(Long entId, LocalDate asOf) {
        LocalDate baseDate = asOf == null ? LocalDate.now() : asOf;
        // 循环外解析一次：结论对整份自查相同，逐项解析只会产生重复审计行
        int expiringWindowDays = resolveExpiringWindowDays();
        List<EnterpriseMaterialCompletenessRespVO.MaterialItem> items = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> expiring = new ArrayList<>();
        int required = 0;
        int ready = 0;

        for (EnterpriseMaterialTypeEnum material : EnterpriseMaterialTypeEnum.values()) {
            if (Boolean.TRUE.equals(material.isMandatory())) {
                required++;
            }
            EnterpriseMaterialCompletenessRespVO.MaterialItem item = checkOne(material, entId, baseDate, expiringWindowDays);
            items.add(item);
            switch (item.getStatus()) {
                case "MISSING" -> {
                    if (Boolean.TRUE.equals(material.isMandatory())) {
                        missing.add(material.getLabel());
                    }
                }
                case "EXPIRED" -> {
                    if (Boolean.TRUE.equals(material.isMandatory())) {
                        missing.add(material.getLabel() + "（已过期）");
                    }
                }
                case "EXPIRING" -> expiring.add(material.getLabel() + "（到期日 " + item.getValidTo() + "）");
                default -> {
                }
            }
            if ("READY".equals(item.getStatus())) {
                ready++;
            }
        }

        EnterpriseMaterialCompletenessRespVO vo = new EnterpriseMaterialCompletenessRespVO();
        vo.setEntId(entId);
        vo.setRequiredCount(required);
        vo.setReadyCount(ready);
        vo.setMissingItems(missing);
        vo.setExpiringItems(expiring);
        vo.setItems(items);
        // 三档口径与 v3.3 表 15-6 对齐：必备缺失/过期=FAIL；仅临近到期=RISK；其余=PASS
        if (!missing.isEmpty()) {
            vo.setResult("FAIL");
        } else if (!expiring.isEmpty()) {
            vo.setResult("RISK");
        } else {
            vo.setResult("PASS");
        }
        return vo;
    }

    // ========== 内部 ==========

    /** 按材料类型的判定来源逐项核验（来源枚举见 EnterpriseMaterialTypeEnum.MaterialSource）。 */
    private EnterpriseMaterialCompletenessRespVO.MaterialItem checkOne(EnterpriseMaterialTypeEnum material,
                                                                      Long entId, LocalDate baseDate, int expiringWindowDays) {
        EnterpriseMaterialCompletenessRespVO.MaterialItem item =
                new EnterpriseMaterialCompletenessRespVO.MaterialItem();
        item.setMaterialType(material.name());
        item.setMaterialLabel(material.getLabel());
        item.setCategory(material.getCategory() == null ? null : material.getCategory().name());
        item.setMandatory(material.isMandatory());
        switch (material.getSource()) {
            case CERT -> fillByCertificate(item, material.getCertType(), entId, baseDate, expiringWindowDays);
            case QUAL -> fillByQualification(item, entId, baseDate, expiringWindowDays);
            case PERMIT -> fillBySafetyPermit(item, entId, baseDate, expiringWindowDays);
            case PERFORMANCE -> fillByPerformance(item, entId);
            default -> fillMissing(item, "未支持的判定来源");
        }
        return item;
    }

    /** 来源一：证照台账 certificate_info（按 cert_type 匹配，取有效期最晚的一条）。 */
    private void fillByCertificate(EnterpriseMaterialCompletenessRespVO.MaterialItem item, String certType,
                                   Long entId, LocalDate baseDate, int expiringWindowDays) {
        if (certType == null) {
            fillMissing(item, "未配置证照类型码");
            return;
        }
        List<CertificateInfoDO> list = certificateInfoMapper.selectList(
                new LambdaQueryWrapper<CertificateInfoDO>()
                        .eq(CertificateInfoDO::getOrgId, entId)
                        .eq(CertificateInfoDO::getCertType, certType)
                        .eq(CertificateInfoDO::getCertStatus, "ACTIVE")
                        .orderByDesc(CertificateInfoDO::getValidTo));
        CertificateInfoDO best = list.stream()
                .filter(c -> c.getValidTo() == null || !c.getValidTo().isBefore(baseDate))
                .findFirst().orElse(null);
        if (best == null) {
            fillMissing(item, list.isEmpty() ? "证照台账中未登记该类材料" : "台账中该类材料均已过期/失效");
            return;
        }
        applyValidTo(item, best.getId(), best.getCertName(), best.getValidTo(), baseDate, expiringWindowDays);
    }

    /** 来源二：企业资质主档 enterprise_qualification（在有效期内即视为具备）。 */
    private void fillByQualification(EnterpriseMaterialCompletenessRespVO.MaterialItem item, Long entId,
                                     LocalDate baseDate, int expiringWindowDays) {
        List<EnterpriseQualificationDO> list = enterpriseQualificationMapper.selectList(
                new LambdaQueryWrapper<EnterpriseQualificationDO>()
                        .eq(EnterpriseQualificationDO::getEntId, entId)
                        .orderByDesc(EnterpriseQualificationDO::getValidTo));
        EnterpriseQualificationDO best = list.stream()
                .filter(q -> q.getValidTo() == null || !q.getValidTo().isBefore(baseDate))
                .findFirst().orElse(null);
        if (best == null) {
            fillMissing(item, list.isEmpty() ? "未登记企业资质" : "企业资质均已过期");
            return;
        }
        applyValidTo(item, best.getId(), best.getQualGrade() + best.getQualCategory() + " " + best.getCertNo(),
                best.getValidTo(), baseDate, expiringWindowDays);
    }

    /** 来源三：安全生产许可证（施工企业必备，3 年一延续）。 */
    private void fillBySafetyPermit(EnterpriseMaterialCompletenessRespVO.MaterialItem item, Long entId,
                                    LocalDate baseDate, int expiringWindowDays) {
        List<QualificationSafetyPermitDO> list = qualificationSafetyPermitMapper.selectList(
                new LambdaQueryWrapper<QualificationSafetyPermitDO>()
                        .eq(QualificationSafetyPermitDO::getEntId, entId)
                        .orderByDesc(QualificationSafetyPermitDO::getValidTo));
        QualificationSafetyPermitDO best = list.stream()
                .filter(p -> p.getValidTo() == null || !p.getValidTo().isBefore(baseDate))
                .findFirst().orElse(null);
        if (best == null) {
            fillMissing(item, list.isEmpty() ? "未登记安全生产许可证" : "安全生产许可证已过期");
            return;
        }
        applyValidTo(item, best.getId(), "安全生产许可证 " + best.getPermitNo(), best.getValidTo(), baseDate, expiringWindowDays);
    }

    /** 来源四：业绩台账（业绩无到期概念，存在即视为具备）。 */
    private void fillByPerformance(EnterpriseMaterialCompletenessRespVO.MaterialItem item, Long entId) {
        Long count = qualificationPerformanceMapper.selectCount(
                new LambdaQueryWrapper<QualificationPerformanceDO>()
                        .eq(QualificationPerformanceDO::getEntId, entId));
        if (count == null || count == 0) {
            fillMissing(item, "未登记工程业绩，无法证明企业实力");
            return;
        }
        item.setStatus("READY");
        item.setRefName("已登记业绩 " + count + " 条");
        item.setMessage("业绩台账已登记");
    }

    private void applyValidTo(EnterpriseMaterialCompletenessRespVO.MaterialItem item, Long refId, String refName,
                              LocalDate validTo, LocalDate baseDate, int expiringWindowDays) {
        item.setRefId(refId);
        item.setRefName(refName);
        item.setValidTo(validTo == null ? null : validTo.toString());
        if (validTo == null) {
            item.setStatus("READY");
            item.setMessage("长期有效");
            return;
        }
        long days = ChronoUnit.DAYS.between(baseDate, validTo);
        if (days <= expiringWindowDays) {
            item.setStatus("EXPIRING");
            item.setMessage("将于 " + days + " 天后到期，请提前办理延续");
        } else {
            item.setStatus("READY");
            item.setMessage("有效（剩余 " + days + " 天）");
        }
    }

    private void fillMissing(EnterpriseMaterialCompletenessRespVO.MaterialItem item, String message) {
        item.setStatus("MISSING");
        item.setMessage(message);
    }
}
