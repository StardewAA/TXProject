package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplicationPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplyReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitPostRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitWithdrawReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.HrmRecruitPostPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 招聘门户接口安全元数据测试。
 *
 * <p>公开查询必须同时放行 Spring Security 和租户过滤器；投递相关接口不得标记
 * {@link PermitAll}，由全局 authenticated 规则保障登录态。</p>
 */
class HrmPortalRecruitControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private HrmPortalRecruitController controller;
    @Mock
    private HrmRecruitPostService postService;
    @Mock
    private HrmEmployeeService employeeService;
    @Mock
    private AdminUserApi adminUserApi;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void publicBrowseEndpointsShouldAllowAnonymousAndNoTenant() throws Exception {
        assertFalse(HrmPortalRecruitController.class.isAnnotationPresent(TenantIgnore.class));

        assertPermitAll("postPage", HrmRecruitPostPageReqVO.class);
        assertPermitAll("postTypeList");
        assertPermitAll("postGet", Long.class);
    }

    @Test
    void applicationEndpointsShouldStillRequireAuthentication() throws Exception {
        assertRequiresAuthentication("applicationCreate", HrmPortalRecruitApplyReqVO.class);
        assertRequiresAuthentication("applicationPage", HrmPortalRecruitApplicationPageReqVO.class);
        assertRequiresAuthentication("applicationGet", Long.class);
        assertRequiresAuthentication("applicationWithdraw", HrmPortalRecruitWithdrawReqVO.class);
    }

    @Test
    void ownerEmployeeLookupFailureShouldNotBreakPublicPostPage() {
        Long tenantId = 101L;
        HrmRecruitPostDO post = createPost(1L, tenantId, 1001L);
        when(postService.getPortalRecruitPostPage(any(HrmRecruitPostPageReqVO.class)))
                .thenReturn(new PageResult<>(Collections.singletonList(post), 1L));
        when(employeeService.getEmployee(post.getOwnerEmployeeId())).thenAnswer(invocation -> {
            assertEquals(tenantId, TenantContextHolder.getTenantId());
            throw new RuntimeException("employee database unavailable");
        });

        CommonResult<PageResult<HrmPortalRecruitPostRespVO>> result =
                controller.postPage(new HrmRecruitPostPageReqVO());

        assertTrue(result.isSuccess());
        assertEquals(1L, result.getData().getTotal());
        HrmPortalRecruitPostRespVO respVO = result.getData().getList().get(0);
        assertNull(respVO.getOwnerUserId());
        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test
    void ownerUserRpcFailureShouldNotBreakPublicPostDetail() {
        Long tenantId = 102L;
        HrmRecruitPostDO post = createPost(2L, tenantId, 1002L);
        HrmEmployeeDO owner = new HrmEmployeeDO();
        owner.setUserId(2002L);
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);
        when(employeeService.getEmployee(post.getOwnerEmployeeId())).thenAnswer(invocation -> {
            assertEquals(tenantId, TenantContextHolder.getTenantId());
            return owner;
        });
        when(adminUserApi.getUserIgnoreTenant(owner.getUserId())).thenAnswer(invocation -> {
            assertEquals(tenantId, TenantContextHolder.getTenantId());
            return CommonResult.error(500, "system user service unavailable");
        });

        CommonResult<HrmPortalRecruitPostRespVO> result = controller.postGet(post.getId());

        assertTrue(result.isSuccess());
        assertNull(result.getData().getOwnerUserId());
        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test
    void postCreatorShouldBeContactWhenOwnerIsNotConfigured() {
        Long tenantId = 103L;
        Long creatorUserId = 2003L;
        HrmRecruitPostDO post = createPost(3L, tenantId, null);
        post.setCreator(String.valueOf(creatorUserId));
        AdminUserRespDTO creator = new AdminUserRespDTO();
        creator.setId(creatorUserId);
        creator.setNickname("岗位发布 HR");
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);
        when(adminUserApi.getUserIgnoreTenant(creatorUserId)).thenAnswer(invocation -> {
            assertEquals(tenantId, TenantContextHolder.getTenantId());
            return CommonResult.success(creator);
        });

        CommonResult<HrmPortalRecruitPostRespVO> result = controller.postGet(post.getId());

        assertTrue(result.isSuccess());
        assertEquals(creatorUserId, result.getData().getOwnerUserId());
        assertEquals("岗位发布 HR", result.getData().getOwnerUserName());
        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    private HrmRecruitPostDO createPost(Long id, Long tenantId, Long ownerEmployeeId) {
        HrmRecruitPostDO post = new HrmRecruitPostDO();
        post.setId(id);
        post.setTenantId(tenantId);
        post.setOwnerEmployeeId(ownerEmployeeId);
        return post;
    }

    private void assertPermitAll(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = HrmPortalRecruitController.class.getDeclaredMethod(methodName, parameterTypes);
        assertNotNull(method.getAnnotation(PermitAll.class), methodName + " should be public");
        assertNotNull(method.getAnnotation(TenantIgnore.class), methodName + " should ignore tenant filtering");
        assertFalse(method.isAnnotationPresent(PreAuthorize.class), methodName + " should not require a permission");
    }

    private void assertRequiresAuthentication(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = HrmPortalRecruitController.class.getDeclaredMethod(methodName, parameterTypes);
        assertFalse(method.isAnnotationPresent(PermitAll.class), methodName + " must not be anonymous");
        assertFalse(method.isAnnotationPresent(PreAuthorize.class),
                methodName + " should rely on global authentication instead of a portal permission");
    }

}
