package cn.iocoder.txgy.module.bidding.service.qualification;

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
 * 与预警扫描口径一致；③ 临近到期阈值取 60 天（法定下限中的常见值：排污/司炉 60 日），
 * 进入该窗口即报 RISK，让企业有办理时间；④ 只读服务——完备性是可重复计算的结论，不落表（同 v3.3 表 15-6 只追加思路）。
 * <p>业务目标：投标前给出一份"材料清单 + 缺口 + 到期风险"，把被动被否决变成主动补齐。
 */
@Slf4j
@Service
public class EnterpriseMaterialServiceImpl implements EnterpriseMaterialService {

    /** 临近到期提醒窗口（天）：进入即判 RISK，为企业预留办理时间。 */
    private static final int EXPIRING_WINDOW_DAYS = 60;

    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    @Resource
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    @Resource
    private QualificationPerformanceMapper qualificationPerformanceMapper;

    @Override
    public EnterpriseMaterialCompletenessRespVO checkCompleteness(Long entId, LocalDate asOf) {
        LocalDate baseDate = asOf == null ? LocalDate.now() : asOf;
        List<EnterpriseMaterialCompletenessRespVO.MaterialItem> items = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> expiring = new ArrayList<>();
        int required = 0;
        int ready = 0;

        for (EnterpriseMaterialTypeEnum material : EnterpriseMaterialTypeEnum.values()) {
            if (Boolean.TRUE.equals(material.isMandatory())) {
                required++;
            }
            EnterpriseMaterialCompletenessRespVO.MaterialItem item = checkOne(material, entId, baseDate);
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
                                                                      Long entId, LocalDate baseDate) {
        EnterpriseMaterialCompletenessRespVO.MaterialItem item =
                new EnterpriseMaterialCompletenessRespVO.MaterialItem();
        item.setMaterialType(material.name());
        item.setMaterialLabel(material.getLabel());
        item.setCategory(material.getCategory() == null ? null : material.getCategory().name());
        item.setMandatory(material.isMandatory());
        switch (material.getSource()) {
            case CERT -> fillByCertificate(item, material.getCertType(), entId, baseDate);
            case QUAL -> fillByQualification(item, entId, baseDate);
            case PERMIT -> fillBySafetyPermit(item, entId, baseDate);
            case PERFORMANCE -> fillByPerformance(item, entId);
            default -> fillMissing(item, "未支持的判定来源");
        }
        return item;
    }

    /** 来源一：证照台账 certificate_info（按 cert_type 匹配，取有效期最晚的一条）。 */
    private void fillByCertificate(EnterpriseMaterialCompletenessRespVO.MaterialItem item, String certType,
                                   Long entId, LocalDate baseDate) {
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
        applyValidTo(item, best.getId(), best.getCertName(), best.getValidTo(), baseDate);
    }

    /** 来源二：企业资质主档 enterprise_qualification（在有效期内即视为具备）。 */
    private void fillByQualification(EnterpriseMaterialCompletenessRespVO.MaterialItem item, Long entId,
                                     LocalDate baseDate) {
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
                best.getValidTo(), baseDate);
    }

    /** 来源三：安全生产许可证（施工企业必备，3 年一延续）。 */
    private void fillBySafetyPermit(EnterpriseMaterialCompletenessRespVO.MaterialItem item, Long entId,
                                    LocalDate baseDate) {
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
        applyValidTo(item, best.getId(), "安全生产许可证 " + best.getPermitNo(), best.getValidTo(), baseDate);
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
                              LocalDate validTo, LocalDate baseDate) {
        item.setRefId(refId);
        item.setRefName(refName);
        item.setValidTo(validTo == null ? null : validTo.toString());
        if (validTo == null) {
            item.setStatus("READY");
            item.setMessage("长期有效");
            return;
        }
        long days = ChronoUnit.DAYS.between(baseDate, validTo);
        if (days <= EXPIRING_WINDOW_DAYS) {
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
