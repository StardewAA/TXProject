package cn.iocoder.txgy.module.hrm.framework.agent.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事实防编造护栏测试。
 *
 * <p>护栏是启发式的，但必须能拦住最典型的编造：凭空多出年份、公司、学校、证书。</p>
 */
public class HrmResumeFactGuardTest {

    @Test
    public void testIntroducedNewYearIsDetected() {
        assertTrue(HrmResumeFactGuard.introducesNewFact(
                "负责管道保温项目施工管理", "负责管道保温项目施工管理，2024 年完成 3 个项目"));
        assertTrue(HrmResumeFactGuard.introducesNewFact(
                "2020 年至 2023 年任职", "2019 年至 2023 年任职"));
    }

    @Test
    public void testReusingExistingYearIsAllowed() {
        assertFalse(HrmResumeFactGuard.introducesNewFact(
                "2021 年负责改造项目", "2021 年主导改造项目，负责施工组织与验收"));
    }

    @Test
    public void testIntroducedNewOrganizationIsDetected() {
        assertTrue(HrmResumeFactGuard.introducesNewFact(
                "负责现场施工管理", "负责现场施工管理，服务过中建三局有限公司"));
        assertTrue(HrmResumeFactGuard.introducesNewFact(
                "本科毕业", "本科毕业于清华大学"));
    }

    @Test
    public void testReusingExistingOrganizationIsAllowed() {
        assertFalse(HrmResumeFactGuard.introducesNewFact(
                "在某某防腐工程有限公司任项目经理",
                "在某某防腐工程有限公司担任项目经理，负责施工组织"));
    }

    @Test
    public void testIntroducedNewCertificateIsDetected() {
        assertTrue(HrmResumeFactGuard.introducesNewFact(
                "熟悉管道保温施工", "熟悉管道保温施工，持一级建造师证"));
    }

    @Test
    public void testReusingExistingCertificateIsAllowed() {
        assertFalse(HrmResumeFactGuard.introducesNewFact(
                "持二级建造师证，熟悉管道保温施工",
                "持二级建造师证，熟练掌握管道保温施工工艺"));
    }

    @Test
    public void testCertificateWordingChangeIsNotTreatedAsNewFact() {
        // 真实场景：「二级建造师证」改写成「二级建造师证书」只是措辞变化，不应判为新增事实
        assertFalse(HrmResumeFactGuard.introducesNewFact(
                "持二级建造师证",
                "持有二级建造师证书"));
        // 但换成原文没有的等级，仍必须拦截
        assertTrue(HrmResumeFactGuard.introducesNewFact(
                "持二级建造师证", "持一级建造师证书"));
    }

    @Test
    public void testBareCertificateNounIsNotTreatedAsFact() {
        // 「证书」是通用名词，单独出现不应被当成具体事实
        assertFalse(HrmResumeFactGuard.introducesNewFact(
                "整理项目资料", "整理项目资料与相关证书"));
    }

    @Test
    public void testPureRewritingIsAllowed() {
        assertFalse(HrmResumeFactGuard.introducesNewFact(
                "负责现场管理",
                "负责施工现场的组织、进度与质量管控，协调甲方与监理"));
    }

    @Test
    public void testBlankInputIsTreatedAsSafe() {
        assertFalse(HrmResumeFactGuard.introducesNewFact(null, null));
        assertFalse(HrmResumeFactGuard.introducesNewFact("原文", null));
        assertFalse(HrmResumeFactGuard.introducesNewFact(null, "建议值"));
    }

    @Test
    public void testExtractorsReturnTokensForDiagnosis() {
        assertTrue(HrmResumeFactGuard.extractYears("2020 年至 2023 年").contains("2020"));
        assertTrue(HrmResumeFactGuard.extractYears("2020 年至 2023 年").contains("2023"));
        assertTrue(HrmResumeFactGuard.extractOrganizations("某某防腐工程有限公司")
                .stream().anyMatch(token -> token.contains("公司")));
    }
}
