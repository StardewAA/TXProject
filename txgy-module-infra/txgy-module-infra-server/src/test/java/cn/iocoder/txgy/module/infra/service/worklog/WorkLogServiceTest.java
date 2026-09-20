package cn.iocoder.txgy.module.infra.service.worklog;

import cn.iocoder.txgy.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.txgy.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.infra.controller.admin.worklog.WorkLogController;
import cn.iocoder.txgy.module.infra.controller.admin.worklog.vo.WorkLogPageReqVO;
import cn.iocoder.txgy.module.infra.dal.mysql.worklog.WorkLogMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Import(WorkLogService.class)
@Sql(scripts="/sql/work_trace.sql", executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class WorkLogServiceTest extends BaseDbUnitTest {
    @Resource WorkLogService service;
    @Resource WorkLogMapper mapper;
    @MockitoBean PermissionCommonApi permissions;
    @BeforeEach void login() {
        TenantContextHolder.setTenantId(11L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(10L).setUserType(2).setTenantId(11L), new org.springframework.mock.web.MockHttpServletRequest());
        when(permissions.hasAnyPermissions(10L, WorkLogService.QUERY_ALL)).thenReturn(CommonResult.success(false));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    private ApiAccessLogCreateReqDTO event(long userId, int code) {
        return new ApiAccessLogCreateReqDTO().setUserId(userId).setUserName("仓储成员" + userId).setUserType(2)
                .setApplicationName("txgy-wms-server").setRequestUrl("/admin-api/wms/material/get")
                .setRequestMethod("GET").setRequestParams("{\"query\":\"{\\\"id\\\":1,\\\"name\\\":\\\"钢管\\\"}\"}")
                .setBeginTime(LocalDateTime.of(2026,9,15,10,0)).setEndTime(LocalDateTime.of(2026,9,15,10,0))
                .setResultCode(code).setUserIp("127.0.0.1").setUserAgent("test").setDuration(2);
    }
    @Test void selfRejectsForgedFilterAndOtherMemberDetailAndExport() throws Exception {
        service.capture(event(10,0)); service.capture(event(99,0));
        var req = new WorkLogPageReqVO(); req.setUserId(99L);
        var self = service.page(req);
        assertEquals(1, self.getTotal()); assertEquals(10L, self.getList().getFirst().getUserId());
        Long other = mapper.selectList().stream().filter(r->r.getUserId()==99L).findFirst().orElseThrow().getId();
        assertThrows(RuntimeException.class, ()->service.get(other,"SELF"));
        req.setScope("ALL");
        assertThrows(RuntimeException.class, ()->service.page(req));
        var response = new MockHttpServletResponse();
        assertThrows(RuntimeException.class, ()->new WorkLogController(service).export(req,response));
        assertEquals(0, response.getContentAsByteArray().length);
    }
    @Test void privilegedQueryStillRestrictsTenantAndSupportsFilters() {
        when(permissions.hasAnyPermissions(10L, WorkLogService.QUERY_ALL)).thenReturn(CommonResult.success(true));
        service.capture(event(10,0)); service.capture(event(99,403));
        TenantContextHolder.setTenantId(22L); service.capture(event(99,0)); TenantContextHolder.setTenantId(11L);
        var req = new WorkLogPageReqVO(); req.setScope("ALL");
        assertEquals(2,service.page(req).getTotal());
        req.setResultStatus("FAILURE"); req.setKeyword("钢管"); req.setModule("wms"); req.setAction("QUERY");
        assertEquals(1,service.page(req).getTotal());
        Long foreign = mapper.selectList().stream().filter(r->r.getTenantId()==22L).findFirst().orElseThrow().getId();
        assertThrows(RuntimeException.class, ()->service.get(foreign,"ALL"));
    }
    @Test void archivePreservesLongRequestsAndMasksSecrets() {
        var event = event(10,0).setRequestParams("{\"note\":\"" + "x".repeat(9000) + "\",\"Password\":\"leakme\"}");
        service.capture(event);
        var row = service.page(new WorkLogPageReqVO()).getList().getFirst();
        assertTrue(row.getRequestParams().length()>9000); assertFalse(row.getRequestParams().contains("leakme"));
        assertEquals("仓储成员10",row.getUserName());
    }
    @Test void distinguishesSubmissionFromApproval() {
        assertEquals("SUBMIT", WorkLogService.action("/admin-api/wms/order/submit-approval", "POST"));
        assertEquals("APPROVE", WorkLogService.action("/admin-api/bpm/task/approve", "PUT"));
    }
    @Test void visitedTenantArchiveCannotLeakBackToOriginalTenant() {
        service.capture(event(10,0).setAuditTenantId(22L));
        assertEquals(0, service.page(new WorkLogPageReqVO()).getTotal());
        assertEquals(11L,TenantContextHolder.getTenantId());
        TenantContextHolder.setTenantId(22L);
        assertEquals(1, service.page(new WorkLogPageReqVO()).getTotal());
    }
    @Test void exportPreservesPayloadLongerThanExcelCellLimit() throws Exception {
        String payload="{\"note\":\"" + "管".repeat(40000) + "\"}";
        service.capture(event(10,0).setRequestParams(payload));
        var response=new MockHttpServletResponse();
        new WorkLogController(service).export(new WorkLogPageReqVO(),response);
        java.util.List<cn.iocoder.txgy.module.infra.controller.admin.worklog.vo.WorkLogRespVO> rows =
                cn.idev.excel.FastExcelFactory.read(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))
                .head(cn.iocoder.txgy.module.infra.controller.admin.worklog.vo.WorkLogRespVO.class).doReadAllSync();
        assertEquals(2,rows.size());
        assertEquals(payload,rows.stream().map(r->r.getRequestParams()==null?"":r.getRequestParams()).collect(java.util.stream.Collectors.joining()));
    }
}
