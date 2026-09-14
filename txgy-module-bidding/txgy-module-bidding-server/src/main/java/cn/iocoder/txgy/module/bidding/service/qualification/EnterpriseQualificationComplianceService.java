package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.enums.QualificationReviewTypeEnum;

import java.time.LocalDate;

/**
 * 企业资质标准库与达标核查服务（M7：标准可版本化 + 四要素聚合核查）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十五 表 15-2/15-6（“要素聚合”：达标核查由 15-3 人员、
 * 15-4 业绩、15-5 资产装备聚合，对照 15-2 标准生成 PASS/RISK/FAIL；element_snapshot 时点快照留证，std_version 可复算）。
 */
public interface EnterpriseQualificationComplianceService {

    /**
     * 登记/升版标准（15-2）：同适用范围同 std_version 判重（UK），生效区间管理现行版本。返回主键。
     */
    Long createStandard(String qualSerial, String qualCategory, String qualGrade, String stdVersion,
                        String reqStaff, String reqAssets, String reqPerformance, String reqEquipment,
                        LocalDate effectiveFrom, LocalDate effectiveTo, String sourceRef);

    /**
     * 执行一次达标核查（15-6 只追加）：
     * 取资质 + 现行标准（缺省按生效区间最新）→ 聚合 staff 有效在册分角色计数 / 近 N 年总承包业绩 / 四库已核验业绩 /
     * 最新年度净资产 → 对照标准 req JSON 键逐项判定：核心要素缺失记 FAIL、其余风险记 RISK、无差异 PASS；
     * element_snapshot 与 diff_items 落 JSON，reviewer/reviewed_at 留痕。返回核查主键。
     */
    Long runComplianceReview(Long qualId, QualificationReviewTypeEnum reviewType, Long reviewer,
                             Long linkedApplyId);
}
