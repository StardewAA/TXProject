package cn.iocoder.txgy.module.hrm.controller.admin.portal.resume;

import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeUploadReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 我的简历接口安全元数据测试。
 *
 * <p>个人账号没有 HRM 后台查询权限，但登录后应能维护自己的简历；用户归属和数据范围由
 * Service 层按登录用户编号控制，接口层只依赖全局 authenticated 规则。</p>
 */
class HrmPortalResumeControllerTest {

    @Test
    void resumeEndpointsShouldNotRequireHrmPermission() throws Exception {
        assertTrue(HrmPortalResumeController.class.isAnnotationPresent(TenantIgnore.class));
        assertNoPermission("uploadResume", HrmPortalResumeUploadReqVO.class);
        assertNoPermission("createResume", HrmPortalResumeCreateReqVO.class);
        assertNoPermission("getResumeList");
    }

    private void assertNoPermission(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = HrmPortalResumeController.class.getDeclaredMethod(methodName, parameterTypes);
        assertFalse(method.isAnnotationPresent(PreAuthorize.class),
                methodName + " should rely on global authentication instead of an HRM permission");
    }
}
