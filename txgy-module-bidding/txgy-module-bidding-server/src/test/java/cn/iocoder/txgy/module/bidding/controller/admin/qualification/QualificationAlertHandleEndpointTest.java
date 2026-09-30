package cn.iocoder.txgy.module.bidding.controller.admin.qualification;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertHandleReqVO;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 预警处理端点契约测试（本仓首个控制器测试，只覆盖"控制器自己那点逻辑"）。
 *
 * <p>规范出处：企业资质页契约 §3.22（预警处理：可闭环处理；处理人取登录态）。
 * <p>为什么值得破例写控制器测试：{@code markAlertHandled} 的服务逻辑（版本 CAS、记录不存在）
 * 已由 CertificateAlertServiceImplTest 覆盖，控制器里唯一属于自己的逻辑就是**处理人取登录态**——
 * 而"处理人不由前端传"是**审计要求**（前端传处理人等于允许代人签字），
 * 这条不变量如果不钉住，将来有人"顺手"把 handledBy 加到请求 VO 上不会有任何测试变红。
 */
class QualificationAlertHandleEndpointTest {

    private CertificateAlertService certificateAlertService;
    private QualificationController controller;

    @BeforeEach
    void setUp() {
        certificateAlertService = mock(CertificateAlertService.class);
        controller = new QualificationController();
        ReflectionTestUtils.setField(controller, "certificateAlertService", certificateAlertService);
    }

    @Test
    void handleAlert_passesLoginUserIdAsHandler_neverFromRequest() {
        QualificationAlertHandleReqVO reqVO = new QualificationAlertHandleReqVO();
        reqVO.setAlertId(7L);
        reqVO.setVersion(0);
        reqVO.setHandleNote("已续期");

        try (MockedStatic<SecurityFrameworkUtils> mocked = Mockito.mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(42L);

            CommonResult<Boolean> result = controller.handleAlert(reqVO);

            assertTrue(result.getData());
            verify(certificateAlertService).markAlertHandled(eq(7L), eq(0), eq(42L), eq("已续期"));
        }
    }

    @Test
    void handleAlert_versionConflict_propagatesInsteadOfSwallowing() {
        QualificationAlertHandleReqVO reqVO = new QualificationAlertHandleReqVO();
        reqVO.setAlertId(7L);
        reqVO.setVersion(1);
        // 版本冲突必须原样抛给前端（前端据此提示"已被他人处理，请刷新"），
        // 控制器不得吞异常、也不得"重试成最后一次写入为准"——那等于静默覆盖他人的处理备注
        // 注意：本用例刻意**不设** handleNote（验证"备注可选"），故桩必须用 any() 而非 anyString()
        // —— anyString() 不匹配 null，桩会静默不生效，测试就会"假绿"
        doThrow(new ServiceException(ErrorCodeConstants.CERT_ALERT_RECORD_MODIFIED))
                .when(certificateAlertService).markAlertHandled(anyLong(), anyInt(), anyLong(), any());

        try (MockedStatic<SecurityFrameworkUtils> mocked = Mockito.mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(42L);
            ServiceException ex = assertThrows(ServiceException.class, () -> controller.handleAlert(reqVO));
            assertEquals(Integer.valueOf(1_060_109_011), ex.getCode());
        }
        verify(certificateAlertService).markAlertHandled(anyLong(), anyInt(), anyLong(), any());
    }
}
