package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 资格门测试（核心规则）：已授权 + 信息完善 → 可投标/发布；
//       仅授权未完善 → 不可投标/发布（但关注/订阅不受影响，属 NoticeFollow 域仅权限控制）。
// 法规基础：招标投标法 第25/26条、实施条例 第19条、电子招标投标办法 第9/13/14/24条（docs/法规依据/00-索引）。

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectEligibilityRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectAuthDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectAuthMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.SubjectAuthStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 主体资格判定服务测试（Mockito）：授权/完善/资格三态 + 招标人发布门/投标人资格门。
 */
class SubjectEligibilityServiceImplTest {

    private SubjectAuthMapper authMapper;
    private SubjectProfileMapper profileMapper;
    private SubjectEligibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        authMapper = mock(SubjectAuthMapper.class);
        profileMapper = mock(SubjectProfileMapper.class);
        service = new SubjectEligibilityServiceImpl();
        ReflectionTestUtils.setField(service, "subjectAuthMapper", authMapper);
        ReflectionTestUtils.setField(service, "subjectProfileMapper", profileMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void resolve_notAuthorized_reasonsAndIneligible() {
        when(authMapper.selectOne(any())).thenReturn(null);

        SubjectEligibilityRespVO resp = service.resolveEligibility(100L, "BIDDER");

        assertFalse(resp.getAuthorized());
        assertFalse(resp.getEligible());
        assertTrue(resp.getReasonCodes().contains("NOT_AUTHORIZED"));
    }

    @Test
    void resolve_authorizedButIncomplete_ineligibleWithMissing() {
        when(authMapper.selectOne(any())).thenReturn(authOf(SubjectAuthStatusEnum.ENABLED));
        SubjectProfileDO profile = profileOf();
        profile.setContactPhone(null); // 必填缺失
        when(profileMapper.selectOne(any())).thenReturn(profile);

        SubjectEligibilityRespVO resp = service.resolveEligibility(100L, "BIDDER");

        assertTrue(resp.getAuthorized(), "授权已获");
        assertFalse(resp.getProfileComplete(), "信息未完善");
        assertFalse(resp.getEligible(), "授权≠完善：不可投标");
        assertTrue(resp.getReasonCodes().contains("PROFILE_INCOMPLETE"));
        assertTrue(resp.getMissingRequiredNames().contains("联系电话"));
    }

    @Test
    void resolve_authorizedAndComplete_eligible() {
        when(authMapper.selectOne(any())).thenReturn(authOf(SubjectAuthStatusEnum.ENABLED));
        when(profileMapper.selectOne(any())).thenReturn(profileOf());

        SubjectEligibilityRespVO resp = service.resolveEligibility(100L, "BIDDER");

        assertTrue(resp.getEligible());
        assertTrue(resp.getReasonCodes().isEmpty());
    }

    @Test
    void resolve_disabledAuth_blocks() {
        when(authMapper.selectOne(any())).thenReturn(authOf(SubjectAuthStatusEnum.DISABLED));
        when(profileMapper.selectOne(any())).thenReturn(profileOf());

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.assertBidderEligible(100L));
        assertEquals(Integer.valueOf(1_060_106_031), e.getCode(), "停用=撤销授权");
    }

    @Test
    void assertBidderEligible_throwsWhenProfileIncomplete() {
        when(authMapper.selectOne(any())).thenReturn(authOf(SubjectAuthStatusEnum.ENABLED));
        when(profileMapper.selectOne(any())).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.assertBidderEligible(100L));
        assertEquals(Integer.valueOf(1_060_106_034), e.getCode(), "仅授权未完善 → 106_034");
    }

    @Test
    void assertTendererIssuanceAllowed_withoutTenantContext_skips() {
        // 无租户上下文（平台同步/初始化）不设门，避免批处理被主体状态阻塞
        TenantContextHolder.setTenantId(null);
        service.assertTendererIssuanceAllowed(); // 不抛即过
    }

    @Test
    void assertTendererIssuanceAllowed_blocksUnAuthorizedTenderer() {
        TenantContextHolder.setTenantId(100L);
        when(authMapper.selectOne(any())).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.assertTendererIssuanceAllowed());
        assertEquals(Integer.valueOf(1_060_106_031), e.getCode(), "未授权招标人不可发文");
    }

    @Test
    void assertTendererIssuanceAllowed_passesWhenEligible() {
        TenantContextHolder.setTenantId(100L);
        when(authMapper.selectOne(any())).thenReturn(authOf(SubjectAuthStatusEnum.ENABLED));
        when(profileMapper.selectOne(any())).thenReturn(profileOf());

        service.assertTendererIssuanceAllowed(); // 不抛即过（招标投标法第8/16条：已授权且完善的招标人可发布）
    }

    @Test
    void resolve_withoutSubjectType_anyEnabledRole_andNoNullInMessage() {
        // 契约 §3.3：不传 subjectType = 任一角色授权 + 唯一档案完善度。
        // 实测回归：不带参调用曾返回 message="主体已授权且信息完善，具备null资格"。
        when(authMapper.selectOne(any())).thenReturn(authOf(SubjectAuthStatusEnum.ENABLED));
        when(profileMapper.selectOne(any())).thenReturn(profileOf());

        SubjectEligibilityRespVO resp = service.resolveEligibility(100L, null);

        assertTrue(resp.getAuthorized(), "任一角色 ENABLED 即视为已授权");
        assertTrue(resp.getEligible());
        assertFalse(resp.getMessage().contains("null"), "角色未指定时不得拼出 null：" + resp.getMessage());
    }

    @Test
    void resolve_withoutSubjectType_disabledRoleStillFallsBackToEnabled() {
        // ENABLED 优先：BIDDER 停用、TENDERER 启用时，不传角色应判为已授权。
        when(authMapper.selectOne(any())).thenReturn(authOf(SubjectAuthStatusEnum.ENABLED));
        when(profileMapper.selectOne(any())).thenReturn(profileOf());
        service.resolveEligibility(100L, null); // 不抛即过

        // 两个角色都停用 → 不可用（回到 DISABLED 语义）
        when(authMapper.selectOne(any())).thenReturn(authOf(SubjectAuthStatusEnum.DISABLED));
        SubjectEligibilityRespVO resp = service.resolveEligibility(100L, null);
        assertFalse(resp.getAuthorized(), "两角色都停用 → 未授权");
    }

    // ========== 工具 ==========

    private SubjectAuthDO authOf(SubjectAuthStatusEnum status) {
        SubjectAuthDO auth = new SubjectAuthDO();
        auth.setId(1L);
        auth.setTenantId(100L);
        auth.setSubjectType("BIDDER");
        auth.setAuthStatus(status.name());
        return auth;
    }

    private SubjectProfileDO profileOf() {
        SubjectProfileDO p = new SubjectProfileDO();
        p.setId(1L);
        p.setSubjectType("BIDDER");
        p.setSubjectName("陕西某保温防腐工程有限公司");
        p.setUscc("91110108MA01ABCDEF");
        p.setLegalPersonName("张三");
        p.setLegalPersonTypeCode("1");
        p.setRegionCode("610100");
        p.setContactName("李四");
        p.setContactPhone("13800000000");
        return p;
    }
}
