package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 平台授权与经办用户管理测试：授权=核发（可停用/复用）；列表行带档案完善度（授权≠完善，需求口径）。

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserBindReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectAuthDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectUserDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectAuthMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectUserMapper;
import cn.iocoder.txgy.module.bidding.enums.SubjectAuthStatusEnum;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主体授权与经办用户服务测试（Mockito，G 轨手动租户条件 + 跨租户 T 轨读取 executeIgnore）。
 */
class SubjectAuthServiceImplTest {

    private SubjectAuthMapper authMapper;
    private SubjectProfileMapper profileMapper;
    private SubjectUserMapper userMapper;
    private AdminUserApi adminUserApi;
    private SubjectAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        authMapper = mock(SubjectAuthMapper.class);
        profileMapper = mock(SubjectProfileMapper.class);
        userMapper = mock(SubjectUserMapper.class);
        adminUserApi = mock(AdminUserApi.class);
        service = new SubjectAuthServiceImpl();
        ReflectionTestUtils.setField(service, "subjectAuthMapper", authMapper);
        ReflectionTestUtils.setField(service, "subjectProfileMapper", profileMapper);
        ReflectionTestUtils.setField(service, "subjectUserMapper", userMapper);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
    }

    @Test
    void authorize_newInsertEnabled() {
        when(authMapper.selectOne(any())).thenReturn(null);
        when(authMapper.insert(any(SubjectAuthDO.class))).thenAnswer(inv -> {
            inv.getArgument(0, SubjectAuthDO.class).setId(1L);
            return 1;
        });

        Long id = service.authorize(saveReq(100L, "BIDDER"));

        assertEquals(1L, id);
        verify(authMapper).insert(any(SubjectAuthDO.class));
    }

    @Test
    void authorize_duplicateThrows() {
        SubjectAuthDO exists = authOf(100L, "BIDDER", SubjectAuthStatusEnum.ENABLED);
        when(authMapper.selectOne(any())).thenReturn(exists);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.authorize(saveReq(100L, "BIDDER")));
        assertEquals(Integer.valueOf(1_060_106_032), e.getCode(), "已授权应报重复");
    }

    @Test
    void authorize_crossTenant_runsInTargetTenantContext() {
        // [契约 e2e 追加项 #1 回归] 平台租户（如 1）的 admin 为其他租户（如 2019）授权时，
        // 写库必须在目标租户上下文内执行（否则租户插件按登录租户填充 tenant_id → 落错租户/撞 UK 成 500），
        // 且执行完毕必须恢复登录租户上下文（不能污染后续请求）。
        when(authMapper.selectOne(any())).thenReturn(null);
        AtomicLong seenTenant = new AtomicLong();
        when(authMapper.insert(any(SubjectAuthDO.class))).thenAnswer(inv -> {
            seenTenant.set(TenantContextHolder.getTenantId());
            inv.getArgument(0, SubjectAuthDO.class).setId(9L);
            return 1;
        });

        TenantContextHolder.setTenantId(1L);
        try {
            Long id = service.authorize(saveReq(2019L, "BIDDER"));
            assertEquals(9L, id);
            assertEquals(2019L, seenTenant.get(), "必须在目标租户上下文写入");
            assertEquals(1L, TenantContextHolder.getTenantId(), "执行后应恢复登录租户上下文");
        } finally {
            TenantContextHolder.setTenantId(null);
        }
    }

    @Test
    void authorize_disabledHistoryReenabled() {
        SubjectAuthDO exists = authOf(100L, "BIDDER", SubjectAuthStatusEnum.DISABLED);
        when(authMapper.selectOne(any())).thenReturn(exists);

        service.authorize(saveReq(100L, "BIDDER"));

        verify(authMapper).updateById(exists);
        assertEquals(SubjectAuthStatusEnum.ENABLED.name(), exists.getAuthStatus());
    }

    @Test
    void updateStatus_disableSetsTime() {
        SubjectAuthDO auth = authOf(100L, "BIDDER", SubjectAuthStatusEnum.ENABLED);
        when(authMapper.selectById(9L)).thenReturn(auth);

        service.updateAuthStatus(9L, SubjectAuthStatusEnum.DISABLED.name());

        assertEquals(SubjectAuthStatusEnum.DISABLED.name(), auth.getAuthStatus());
        assertNotNull(auth.getDisableTime(), "停用应留痕 disable_time");
    }

    @Test
    void getAuthPage_enrichesProfileCompleteness() {
        SubjectAuthDO auth = authOf(100L, "BIDDER", SubjectAuthStatusEnum.ENABLED);
        Page<SubjectAuthDO> page = Page.of(1, 10);
        page.setRecords(List.of(auth));
        page.setTotal(1);
        when(authMapper.selectPage(any(Page.class), any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(page);
        // 档案缺联系电话 → 行内完善度=false
        SubjectProfileDO profile = new SubjectProfileDO();
        profile.setSubjectType("BIDDER");
        profile.setSubjectName("陕西某公司");
        profile.setUscc("91110108MA01ABCDEF");
        profile.setLegalPersonName("张三");
        profile.setLegalPersonTypeCode("1");
        profile.setRegionCode("610100");
        profile.setContactName("李四");
        profile.setContactPhone(null);
        when(profileMapper.selectOne(any())).thenReturn(profile);

        PageResult<cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthRespVO> result =
                service.getAuthPage(new SubjectAuthPageReqVO());

        assertEquals(1, result.getList().size());
        assertFalse(result.getList().get(0).getProfileComplete(), "授权但未完善应在列表行体现");
    }

    @Test
    void bindUser_successWithNicknameSnapshot() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(adminUserApi.validateUserList(any())).thenReturn(CommonResult.success(Boolean.TRUE));
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(88L);
        user.setNickname("小王");
        when(adminUserApi.getUser(88L)).thenReturn(CommonResult.success(user));
        when(userMapper.insert(any(SubjectUserDO.class))).thenAnswer(inv -> {
            inv.getArgument(0, SubjectUserDO.class).setId(5L);
            return 1;
        });

        SubjectUserBindReqVO req = new SubjectUserBindReqVO();
        req.setTenantId(100L);
        req.setSubjectType("BIDDER");
        req.setUserId(88L);
        Long id = service.bindUser(req);

        assertEquals(5L, id);
        org.mockito.ArgumentCaptor<SubjectUserDO> captor = org.mockito.ArgumentCaptor.forClass(SubjectUserDO.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("小王", captor.getValue().getUserNickname());
    }

    @Test
    void bindUser_rejectsInvalidUser() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(adminUserApi.validateUserList(any())).thenReturn(CommonResult.success(Boolean.FALSE));

        SubjectUserBindReqVO req = new SubjectUserBindReqVO();
        req.setTenantId(100L);
        req.setSubjectType("BIDDER");
        req.setUserId(99L);
        ServiceException e = assertThrows(ServiceException.class, () -> service.bindUser(req));
        assertEquals(Integer.valueOf(1_060_106_040), e.getCode(), "用户无效应拒绝绑定");
    }

    @Test
    void unbindUser_notFoundThrows() {
        when(userMapper.selectById(3L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.unbindUser(3L));
        assertEquals(Integer.valueOf(1_060_106_038), e.getCode());
    }

    // ========== 工具 ==========

    private SubjectAuthSaveReqVO saveReq(Long tenantId, String type) {
        SubjectAuthSaveReqVO req = new SubjectAuthSaveReqVO();
        req.setTenantId(tenantId);
        req.setTenantName("陕西某保温防腐工程有限公司");
        req.setSubjectType(type);
        return req;
    }

    private SubjectAuthDO authOf(Long tenantId, String type, SubjectAuthStatusEnum status) {
        SubjectAuthDO auth = new SubjectAuthDO();
        auth.setId(1L);
        auth.setTenantId(tenantId);
        auth.setTenantName("陕西某保温防腐工程有限公司");
        auth.setSubjectType(type);
        auth.setAuthStatus(status.name());
        return auth;
    }
}
