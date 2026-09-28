package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.module.cms.enums.CmsSearchScopeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CmsSearchRouterTest {

    @Test
    void shouldForceLawScopeForLegalIdentifiers() {
        assertEquals(CmsSearchScopeEnum.LAW,
                CmsSearchRouter.resolve(CmsSearchScopeEnum.AUTO, "GB/T 23257-2017 第十条怎么规定"));
        assertEquals(CmsSearchScopeEnum.LAW,
                CmsSearchRouter.resolve(null, "根据安全生产法，企业有哪些责任"));
    }

    @Test
    void shouldKeepExplicitScopeAndUseAllForOrdinaryQuestion() {
        assertEquals(CmsSearchScopeEnum.QA,
                CmsSearchRouter.resolve(CmsSearchScopeEnum.QA, "根据某法如何处理"));
        assertEquals(CmsSearchScopeEnum.ALL,
                CmsSearchRouter.resolve(CmsSearchScopeEnum.AUTO, "设备发生故障后应该怎么办"));
    }

    @Test
    void shouldRouteEnterpriseSubsidyQuestionToFinancePolicy() {
        assertEquals(CmsSearchScopeEnum.FINANCE_POLICY,
                CmsSearchRouter.resolve(CmsSearchScopeEnum.AUTO, "杭州制造业贷款贴息申报截止时间"));
    }
}
