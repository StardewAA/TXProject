package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationApplyRecordDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationApplyRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationComplianceReviewMapper;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAssetRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationElementPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPerformanceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPermitPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPermitRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationReviewPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationReviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStaffRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationAssetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationPerformanceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationAssetMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationPerformanceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStaffConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStandardMapper;
import cn.iocoder.txgy.module.bidding.enums.QualificationApplyStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationApplyTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationComplianceReviewDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStandardDO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardOptionsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardRespVO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业资质主档/申报流服务规则测试（Mockito，TDD）：
 * 建档 UK 判重/关联台账校验、状态机非法流转、延续申报（PENDING_RENEW）、审批通过办结回写（VALID+new_valid_to）、
 * 退回还原、乐观锁冲突。依据：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1/15-7。
 */
class EnterpriseQualificationServiceImplTest {

    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    private QualificationApplyRecordMapper qualificationApplyRecordMapper;
    private CertificateInfoMapper certificateInfoMapper;
    private QualificationComplianceReviewMapper qualificationComplianceReviewMapper;
    private QualificationStandardMapper qualificationStandardMapper;
    private QualificationStaffConfigMapper qualificationStaffConfigMapper;
    private QualificationPerformanceMapper qualificationPerformanceMapper;
    private QualificationAssetMapper qualificationAssetMapper;
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    private CertAlertRecordMapper certAlertRecordMapper;
    private cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService subjectProfileService;
    private cn.iocoder.txgy.module.bidding.service.review.ReviewTaskService reviewTaskService;
    private EnterpriseQualificationServiceImpl service;

    @BeforeEach
    void setUp() {
        enterpriseQualificationMapper = mock(EnterpriseQualificationMapper.class);
        qualificationApplyRecordMapper = mock(QualificationApplyRecordMapper.class);
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        qualificationComplianceReviewMapper = mock(QualificationComplianceReviewMapper.class);
        qualificationStandardMapper = mock(QualificationStandardMapper.class);
        qualificationStaffConfigMapper = mock(QualificationStaffConfigMapper.class);
        qualificationPerformanceMapper = mock(QualificationPerformanceMapper.class);
        qualificationAssetMapper = mock(QualificationAssetMapper.class);
        qualificationSafetyPermitMapper = mock(QualificationSafetyPermitMapper.class);
        certAlertRecordMapper = mock(CertAlertRecordMapper.class);
        service = new EnterpriseQualificationServiceImpl();
        ReflectionTestUtils.setField(service, "enterpriseQualificationMapper", enterpriseQualificationMapper);
        ReflectionTestUtils.setField(service, "qualificationApplyRecordMapper", qualificationApplyRecordMapper);
        ReflectionTestUtils.setField(service, "certificateInfoMapper", certificateInfoMapper);
        ReflectionTestUtils.setField(service, "qualificationComplianceReviewMapper", qualificationComplianceReviewMapper);
        ReflectionTestUtils.setField(service, "qualificationStandardMapper", qualificationStandardMapper);
        ReflectionTestUtils.setField(service, "qualificationStaffConfigMapper", qualificationStaffConfigMapper);
        ReflectionTestUtils.setField(service, "qualificationPerformanceMapper", qualificationPerformanceMapper);
        ReflectionTestUtils.setField(service, "qualificationAssetMapper", qualificationAssetMapper);
        ReflectionTestUtils.setField(service, "qualificationSafetyPermitMapper", qualificationSafetyPermitMapper);
        ReflectionTestUtils.setField(service, "certAlertRecordMapper", certAlertRecordMapper);
        // 企业锚点由后端推导（§12 口径：本企业主体 id 由后端取）；建档触发审查任务（契约《平台审查任务中心》）
        subjectProfileService = mock(cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService.class);
        ReflectionTestUtils.setField(service, "subjectProfileService", subjectProfileService);
        org.mockito.Mockito.lenient().when(subjectProfileService.getOwnSubjectId()).thenReturn(1L);
        reviewTaskService = mock(cn.iocoder.txgy.module.bidding.service.review.ReviewTaskService.class);
        ReflectionTestUtils.setField(service, "reviewTaskService", reviewTaskService);
    }

    @Test
    void create_ok_returnsIdWithDefaults() {
        when(enterpriseQualificationMapper.selectByCertNo(any(), any(), any(), any(), any())).thenReturn(null);
        when(enterpriseQualificationMapper.insert(any(EnterpriseQualificationDO.class))).thenAnswer(inv -> {
            EnterpriseQualificationDO d = inv.getArgument(0);
            d.setId(31L);
            return 1;
        });

        Long id = service.createQualification(req(null));

        assertEquals(31L, id);
        ArgumentCaptor<EnterpriseQualificationDO> captor = ArgumentCaptor.forClass(EnterpriseQualificationDO.class);
        verify(enterpriseQualificationMapper).insert(captor.capture());
        assertEquals(QualificationStatusEnum.VALID.name(), captor.getValue().getQualStatus());
        assertEquals("MANUAL", captor.getValue().getSourceChannel());
        assertEquals("CONSTRUCTION", captor.getValue().getQualSerial());
    }

    @Test
    void create_duplicateUkRejected() {
        when(enterpriseQualificationMapper.selectByCertNo(any(), any(), any(), any(), any()))
                .thenReturn(new EnterpriseQualificationDO());

        ServiceException e = assertThrows(ServiceException.class, () -> service.createQualification(req(null)));
        assertEquals(Integer.valueOf(1_060_109_016), e.getCode(), "UK 命中应报 QUALIFICATION_NO_DUPLICATE");
        verify(enterpriseQualificationMapper, never()).insert(any(EnterpriseQualificationDO.class));
    }

    @Test
    void create_bindedCertNotExistsRejected() {
        when(enterpriseQualificationMapper.selectByCertNo(any(), any(), any(), any(), any())).thenReturn(null);
        when(certificateInfoMapper.selectById(88L)).thenReturn(null);

        EnterpriseQualificationSaveReqVO r = req(null);
        r.setBindedCertId(88L);
        ServiceException e = assertThrows(ServiceException.class, () -> service.createQualification(r));
        assertEquals(Integer.valueOf(1_060_109_000), e.getCode(), "关联台账证书不存在应报 CERTIFICATE_NOT_EXISTS");
    }

    @Test
    void create_bindedCertExistsPasses() {
        when(enterpriseQualificationMapper.selectByCertNo(any(), any(), any(), any(), any())).thenReturn(null);
        when(certificateInfoMapper.selectById(88L)).thenReturn(new CertificateInfoDO());
        when(enterpriseQualificationMapper.insert(any(EnterpriseQualificationDO.class))).thenAnswer(inv -> {
            EnterpriseQualificationDO d = inv.getArgument(0);
            d.setId(31L);
            return 1;
        });
        EnterpriseQualificationSaveReqVO r = req(null);
        r.setBindedCertId(88L);
        assertEquals(31L, service.createQualification(r));
    }

    @Test
    void changeStatus_invalidTransitionRejected() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.REVOKED); // 终态
        qual.setVersion(1);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.changeStatus(1L, QualificationStatusEnum.VALID.name(), 1, null, 42L));
        assertEquals(Integer.valueOf(1_060_109_017), e.getCode(), "终态不可恢复应报状态流转非法");
        verify(enterpriseQualificationMapper, never()).updateById(any(EnterpriseQualificationDO.class));
    }

    @Test
    void changeStatus_versionMismatchRejected() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.VALID);
        qual.setVersion(5);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.changeStatus(1L, QualificationStatusEnum.EXPIRED.name(), 2, null, null));
        // 版本先于枚举解析校验：确保 version 冲突在迁移表解析前拦截
        assertEquals(Integer.valueOf(1_060_109_018), e.getCode());
    }

    @Test
    void submitRenewal_createsApplyAndMarksPendingRenew() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.VALID);
        qual.setVersion(3);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);
        when(qualificationApplyRecordMapper.insert(any(QualificationApplyRecordDO.class))).thenAnswer(inv -> {
            QualificationApplyRecordDO a = inv.getArgument(0);
            a.setId(50L);
            return 1;
        });
        when(enterpriseQualificationMapper.updateById(any(EnterpriseQualificationDO.class))).thenReturn(1);

        Long applyId = service.submitRenewalApply(1L, 3, 42L,
                LocalDate.of(2026, 10, 31), "证书到期延续申报");

        assertEquals(50L, applyId);
        assertEquals(QualificationStatusEnum.PENDING_RENEW.name(), qual.getQualStatus());
        ArgumentCaptor<QualificationApplyRecordDO> captor = ArgumentCaptor.forClass(QualificationApplyRecordDO.class);
        verify(qualificationApplyRecordMapper).insert(captor.capture());
        assertEquals(QualificationApplyTypeEnum.RENEW.name(), captor.getValue().getApplyType());
        assertEquals(QualificationApplyStatusEnum.SUBMITTED.name(), captor.getValue().getStatus());
        assertEquals(42L, captor.getValue().getOwnerBy());
    }

    @Test
    void submitRenewal_fromRevokedRejected() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.REVOKED);
        qual.setVersion(0);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.submitRenewalApply(1L, 0, 42L, null, null));
        assertEquals(Integer.valueOf(1_060_109_017), e.getCode());
        verify(qualificationApplyRecordMapper, never()).insert(any(QualificationApplyRecordDO.class));
    }

    @Test
    void approveRenewal_writesBackValidAndNewValidTo() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.PENDING_RENEW);
        qual.setVersion(2);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);
        QualificationApplyRecordDO apply = applyOf(50L, QualificationApplyStatusEnum.SUBMITTED);
        apply.setVersion(0);
        when(qualificationApplyRecordMapper.selectById(50L)).thenReturn(apply);
        when(qualificationApplyRecordMapper.updateById(any(QualificationApplyRecordDO.class))).thenReturn(1);
        when(enterpriseQualificationMapper.updateById(any(EnterpriseQualificationDO.class))).thenReturn(1);

        Long id = service.approveRenewalApply(50L, 0, "建审〔2026〕99号", "D-NEW-001",
                LocalDate.of(2029, 12, 31), 7L);

        assertEquals(50L, id);
        assertEquals(QualificationStatusEnum.VALID.name(), qual.getQualStatus(), "办结回写 VALID");
        assertEquals(LocalDate.of(2029, 12, 31), qual.getValidTo(), "回写延续后 valid_to");
        assertEquals("D-NEW-001", qual.getCertNo());
        // 申报流：APPROVED→ISSUED 两次推进
        verify(qualificationApplyRecordMapper, times(2)).updateById(any(QualificationApplyRecordDO.class));
        assertEquals(QualificationApplyStatusEnum.ISSUED.name(), apply.getStatus());
        assertEquals("建审〔2026〕99号", apply.getResultNo());
    }

    @Test
    void approveRenewal_missingNewValidToRejected() {
        QualificationApplyRecordDO apply = applyOf(50L, QualificationApplyStatusEnum.SUBMITTED);
        apply.setVersion(0);
        when(qualificationApplyRecordMapper.selectById(50L)).thenReturn(apply);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.approveRenewalApply(50L, 0, "文号", null, null, 7L));
        assertEquals(Integer.valueOf(1_060_109_021), e.getCode(), "延续必须给新有效期");
        verify(enterpriseQualificationMapper, never()).updateById(any(EnterpriseQualificationDO.class));
    }

    @Test
    void rejectRenewal_restoresValid() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.PENDING_RENEW);
        qual.setVersion(1);
        when(enterpriseQualificationMapper.selectById(1L)).thenReturn(qual);
        QualificationApplyRecordDO apply = applyOf(50L, QualificationApplyStatusEnum.SUBMITTED);
        apply.setVersion(0);
        when(qualificationApplyRecordMapper.selectById(50L)).thenReturn(apply);
        when(qualificationApplyRecordMapper.updateById(any(QualificationApplyRecordDO.class))).thenReturn(1);
        when(enterpriseQualificationMapper.updateById(any(EnterpriseQualificationDO.class))).thenReturn(1);

        service.rejectRenewalApply(50L, 0, 7L, "材料不齐");

        assertEquals(QualificationApplyStatusEnum.REJECTED.name(), apply.getStatus());
        assertEquals(QualificationStatusEnum.VALID.name(), qual.getQualStatus(), "退回还原 VALID");
        assertEquals("材料不齐", apply.getRemark());
    }

    // ==================== 查询（契约 §4 G1~G3） ====================

    @Test
    void getQualificationPage_ok_computesDaysToExpireAndPendingFlag() {
        EnterpriseQualificationDO expiringSoon = qualOf(QualificationStatusEnum.EXPIRING_SOON);
        expiringSoon.setId(9001L);
        expiringSoon.setEntId(1L);
        expiringSoon.setValidTo(LocalDate.now().plusDays(30));
        EnterpriseQualificationDO longTerm = qualOf(QualificationStatusEnum.VALID);
        longTerm.setId(9002L);
        longTerm.setEntId(1L);
        longTerm.setValidTo(null); // 长期
        Page<EnterpriseQualificationDO> page = new Page<>(1, 10);
        page.setRecords(List.of(expiringSoon, longTerm));
        page.setTotal(2L);
        when(enterpriseQualificationMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<EnterpriseQualificationDO>>any(), any())).thenReturn(page);
        QualificationApplyRecordDO reviewing = new QualificationApplyRecordDO();
        reviewing.setQualId(9001L);
        reviewing.setStatus(QualificationApplyStatusEnum.REVIEWING.name()); // 在途（非 SUBMITTED 也要算）
        when(qualificationApplyRecordMapper.selectList(any())).thenReturn(List.of(reviewing));

        QualificationPageReqVO reqVO = new QualificationPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationRespVO> result = service.getQualificationPage(reqVO);

        assertEquals(2L, result.getTotal());
        assertEquals(30, result.getList().get(0).getDaysToExpire().intValue());
        assertEquals(Boolean.TRUE, result.getList().get(0).getHasPendingApply());
        assertNull(result.getList().get(1).getDaysToExpire(), "长期（valid_to 空）必须返回 null，不能算成 0 天");
        assertEquals(Boolean.FALSE, result.getList().get(1).getHasPendingApply());
    }

    @Test
    void getQualificationPage_missingEntId_throws() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.getQualificationPage(new QualificationPageReqVO()));
        assertEquals(ErrorCodeConstants.QUALIFICATION_QUERY_ENT_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void getQualification_ok_fillsBindedCertAndLatestReview() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.VALID);
        qual.setId(9001L);
        qual.setBindedCertId(501L);
        when(enterpriseQualificationMapper.selectById(9001L)).thenReturn(qual);
        CertificateInfoDO cert = new CertificateInfoDO();
        cert.setCertName("建筑业企业资质证书");
        cert.setCertNo("D361123456");
        cert.setCertStatus("VALID");
        when(certificateInfoMapper.selectById(501L)).thenReturn(cert);
        when(qualificationApplyRecordMapper.selectList(any())).thenReturn(List.of());
        QualificationComplianceReviewDO review = new QualificationComplianceReviewDO();
        review.setStdVersion("v2026.1");
        review.setReviewResult("PASS");
        review.setReviewedAt(LocalDateTime.of(2026, 1, 5, 10, 0));
        when(qualificationComplianceReviewMapper.selectList(any())).thenReturn(List.of(review));

        QualificationDetailRespVO vo = service.getQualification(9001L);

        assertEquals("建筑业企业资质证书", vo.getBindedCertName());
        assertEquals("D361123456", vo.getBindedCertNo());
        assertEquals("v2026.1", vo.getLatestReview().getStdVersion(), "核查结论必须带依据的标准版本，否则不可作审核证据");
        assertEquals("PASS", vo.getLatestReview().getReviewResult());
    }

    @Test
    void getQualification_notExists_throws() {
        when(enterpriseQualificationMapper.selectById(9999L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getQualification(9999L));

        assertEquals(ErrorCodeConstants.QUALIFICATION_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void getQualification_toleratesMissingBindedCert() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.VALID);
        qual.setId(9001L);
        qual.setBindedCertId(777L); // 证照已删/未同步
        when(enterpriseQualificationMapper.selectById(9001L)).thenReturn(qual);
        when(certificateInfoMapper.selectById(777L)).thenReturn(null);
        when(qualificationApplyRecordMapper.selectList(any())).thenReturn(List.of());
        when(qualificationComplianceReviewMapper.selectList(any())).thenReturn(List.of());

        QualificationDetailRespVO vo = service.getQualification(9001L);

        assertNull(vo.getBindedCertName(), "证照缺失时详情不应报错，摘要留空由前端走空态");
        assertNull(vo.getLatestReview());
    }

    @Test
    void getApplyPage_ok_mapsStatusAndVersion() {
        QualificationApplyRecordDO record = new QualificationApplyRecordDO();
        record.setId(88001L);
        record.setEntId(1L);
        record.setQualId(9001L);
        record.setApplyType(QualificationApplyTypeEnum.RENEW.name());
        record.setStatus(QualificationApplyStatusEnum.SUBMITTED.name());
        record.setSubmitDate(LocalDate.of(2026, 1, 5));
        record.setVersion(1);
        Page<QualificationApplyRecordDO> page = new Page<>(1, 10);
        page.setRecords(List.of(record));
        page.setTotal(1L);
        when(qualificationApplyRecordMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<QualificationApplyRecordDO>>any(), any())).thenReturn(page);

        QualificationApplyPageReqVO reqVO = new QualificationApplyPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setStatus(QualificationApplyStatusEnum.SUBMITTED.name());
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationApplyRespVO> result = service.getApplyPage(reqVO);

        assertEquals(1L, result.getTotal());
        assertEquals(QualificationApplyTypeEnum.RENEW.name(), result.getList().get(0).getApplyType());
        assertEquals(Integer.valueOf(1), result.getList().get(0).getVersion(), "审批动作要直接回传 version");
    }

    // ==================== 查询：资质标准（契约 §4 G4） ====================

    @Test
    void getStandardPage_ok_computesEffectiveFlag() {
        QualificationStandardDO effective = standardOf("施工", "市政公用工程施工总承包", "二级", "v2026.1");
        effective.setId(3001L);
        effective.setEffectiveFrom(LocalDate.now().minusDays(10));
        effective.setEffectiveTo(null); // 长期有效
        QualificationStandardDO expired = standardOf("施工", "建筑工程施工总承包", "一级", "v2020.1");
        expired.setId(3002L);
        expired.setEffectiveFrom(LocalDate.now().minusYears(3));
        expired.setEffectiveTo(LocalDate.now().minusDays(1)); // 已失效
        Page<QualificationStandardDO> page = new Page<>(1, 10);
        page.setRecords(List.of(effective, expired));
        page.setTotal(2L);
        when(qualificationStandardMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<QualificationStandardDO>>any(), any())).thenReturn(page);

        QualificationStandardPageReqVO reqVO = new QualificationStandardPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationStandardRespVO> result = service.getStandardPage(reqVO);

        assertEquals(Boolean.TRUE, result.getList().get(0).getIsEffective());
        assertEquals(Boolean.FALSE, result.getList().get(1).getIsEffective(), "截止日已过必须判为失效，否则核查会拿废止标准当依据");
        assertEquals("v2026.1", result.getList().get(0).getStdVersion());
    }

    @Test
    void getStandardOptions_ok_buildsCascade() {
        when(qualificationStandardMapper.selectList(any())).thenReturn(List.of(
                standardOf("施工", "市政公用工程施工总承包", "一级", "v2026.1"),
                standardOf("施工", "市政公用工程施工总承包", "二级", "v2026.1"),
                standardOf("施工", "建筑工程施工总承包", "一级", "v2026.1"),
                standardOf("监理", "市政公用工程监理", "甲级", "v2026.1")));

        QualificationStandardOptionsRespVO options = service.getStandardOptions();

        assertEquals(2, options.getSerials().size(), "两个序列");
        assertEquals(2, options.getSerials().get(0).getCategories().size(), "施工序列下两个类别");
        assertEquals(List.of("一级", "二级"), options.getSerials().get(0).getCategories().get(0).getGrades(),
                "等级按库内顺序去重，供三级级联下拉直接使用");
        assertEquals(List.of("v2026.1"), options.getStdVersions());
    }

    @Test
    void getStandardOptions_emptyWhenNoEffectiveStandard() {
        when(qualificationStandardMapper.selectList(any())).thenReturn(List.of());

        QualificationStandardOptionsRespVO options = service.getStandardOptions();

        assertTrue(options.getSerials().isEmpty(), "过渡期无现行标准时返回空结构，前端引导去登记标准而不是报错");
        assertTrue(options.getStdVersions().isEmpty());
    }

    private QualificationStandardDO standardOf(String serial, String category, String grade, String version) {
        QualificationStandardDO std = new QualificationStandardDO();
        std.setQualSerial(serial);
        std.setQualCategory(category);
        std.setQualGrade(grade);
        std.setStdVersion(version);
        std.setSourceRef("建市〔2014〕159号");
        return std;
    }

    // ==================== 查询：要素台账 / 核查历史 / 安许 / 预警（契约 §4 G5~G8） ====================

    @Test
    void staffRespVO_neverCarriesIdCardFields() {
        assertTrue(Arrays.stream(QualificationStaffRespVO.class.getDeclaredFields())
                        .noneMatch(f -> f.getName().toLowerCase().contains("idcard")),
                "身份证密文/盲索引一律不得出现在出参 VO 上（L4 加密的最小必要字段原则，靠 DTO 不承载而非前端不显示）");
    }

    @Test
    void getStaffPage_ok_mapsFieldsWithoutIdCard() {
        QualificationStaffConfigDO staff = new QualificationStaffConfigDO();
        staff.setId(7001L);
        staff.setEntId(1L);
        staff.setQualId(9001L);
        staff.setPersonName("张三");
        staff.setStaffRole("注册建造师");
        staff.setIdCardEnc("ENCRYPTED::xxx"); // 源表有密文，出参必须没有
        Page<QualificationStaffConfigDO> page = new Page<>(1, 10);
        page.setRecords(List.of(staff));
        page.setTotal(1L);
        when(qualificationStaffConfigMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<QualificationStaffConfigDO>>any(), any())).thenReturn(page);

        QualificationElementPageReqVO reqVO = new QualificationElementPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationStaffRespVO> result = service.getStaffPage(reqVO);

        assertEquals("张三", result.getList().get(0).getPersonName());
        assertEquals("注册建造师", result.getList().get(0).getStaffRole());
    }

    @Test
    void getPerformancePage_ok_mapsPlatformVerified() {
        QualificationPerformanceDO perf = new QualificationPerformanceDO();
        perf.setId(7101L);
        perf.setEntId(1L);
        perf.setProjectName("XX 市政道路工程");
        perf.setIsPlatformVerified(Boolean.TRUE);
        Page<QualificationPerformanceDO> page = new Page<>(1, 10);
        page.setRecords(List.of(perf));
        page.setTotal(1L);
        when(qualificationPerformanceMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<QualificationPerformanceDO>>any(), any())).thenReturn(page);

        QualificationElementPageReqVO reqVO = new QualificationElementPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationPerformanceRespVO> result = service.getPerformancePage(reqVO);

        assertEquals(Boolean.TRUE, result.getList().get(0).getIsPlatformVerified(),
                "业绩是否平台可核验必须显式给出，审核人要能判断证据强度");
    }

    @Test
    void getAssetPage_ok_keepsStatYear() {
        QualificationAssetDO asset = new QualificationAssetDO();
        asset.setId(7201L);
        asset.setEntId(1L);
        asset.setAssetType("净资产");
        asset.setStatYear((short) 2025);
        Page<QualificationAssetDO> page = new Page<>(1, 10);
        page.setRecords(List.of(asset));
        page.setTotal(1L);
        when(qualificationAssetMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<QualificationAssetDO>>any(), any())).thenReturn(page);

        QualificationElementPageReqVO reqVO = new QualificationElementPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationAssetRespVO> result = service.getAssetPage(reqVO);

        assertEquals(Short.valueOf((short) 2025), result.getList().get(0).getStatYear(),
                "资产按年报口径，年度必须带出，否则达标结论无法复算");
    }

    @Test
    void getReviewPage_ok_returnsStdVersionAndSnapshot() {
        QualificationComplianceReviewDO review = new QualificationComplianceReviewDO();
        review.setId(8001L);
        review.setEntId(1L);
        review.setQualId(9001L);
        review.setStdVersion("v2026.1");
        review.setReviewResult("PASS");
        review.setElementSnapshot("{\"staff\":3}");
        Page<QualificationComplianceReviewDO> page = new Page<>(1, 10);
        page.setRecords(List.of(review));
        page.setTotal(1L);
        when(qualificationComplianceReviewMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<QualificationComplianceReviewDO>>any(), any())).thenReturn(page);

        QualificationReviewPageReqVO reqVO = new QualificationReviewPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationReviewRespVO> result = service.getReviewPage(reqVO);

        assertEquals("v2026.1", result.getList().get(0).getStdVersion());
        assertEquals("{\"staff\":3}", result.getList().get(0).getElementSnapshot(),
                "要素快照必须返回，历史结论要能按当时要素复算");
    }

    @Test
    void getPermitPage_ok_computesDaysToExpire() {
        QualificationSafetyPermitDO permit = new QualificationSafetyPermitDO();
        permit.setId(6001L);
        permit.setEntId(1L);
        permit.setPermitNo("（陕）JZ安许证字〔2023〕001234");
        permit.setValidTo(LocalDate.now().plusDays(120));
        permit.setRiskFlag("B");
        Page<QualificationSafetyPermitDO> page = new Page<>(1, 10);
        page.setRecords(List.of(permit));
        page.setTotal(1L);
        when(qualificationSafetyPermitMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<QualificationSafetyPermitDO>>any(), any())).thenReturn(page);

        QualificationPermitPageReqVO reqVO = new QualificationPermitPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationPermitRespVO> result = service.getPermitPage(reqVO);

        assertEquals(120, result.getList().get(0).getDaysToExpire().intValue());
        assertEquals("B", result.getList().get(0).getRiskFlag(), "风险标记随台账返回，风险卡才不至于降级为未知");
    }

    @Test
    void getPermit_notExists_throws() {
        when(qualificationSafetyPermitMapper.selectById(6001L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getPermit(6001L));

        assertEquals(ErrorCodeConstants.SAFETY_PERMIT_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void getAlertPage_carriesVersion_forHandleEndpointCas() {
        // 处理端点 POST /alert/handle 的 version 是 @NotNull 且用于 CAS：读侧不回传，前端只能发假版本
        // （要么恒冲突、要么等于绕过并发控制）——所以"回传 version"必须是读接口的硬要求，用用例钉住。
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.EXPIRING_SOON);
        qual.setId(9001L);
        when(enterpriseQualificationMapper.selectList(any())).thenReturn(List.of(qual));
        when(qualificationSafetyPermitMapper.selectList(any())).thenReturn(List.of());
        CertAlertRecordDO alert = new CertAlertRecordDO();
        alert.setId(4001L);
        alert.setRefType("QUAL");
        alert.setRefId(9001L);
        alert.setVersion(7);
        Page<CertAlertRecordDO> page = new Page<>(1, 10);
        page.setRecords(List.of(alert));
        page.setTotal(1L);
        when(certAlertRecordMapper.selectPage(org.mockito.ArgumentMatchers.<Page<CertAlertRecordDO>>any(), any()))
                .thenReturn(page);

        QualificationAlertPageReqVO reqVO = new QualificationAlertPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationAlertRespVO> result = service.getAlertPage(reqVO);

        assertEquals(7, result.getList().get(0).getVersion(), "清单必须回传 version，否则处理端点接不上");
    }

    @Test
    void getAlertPage_withoutAnyQualOrPermit_returnsEmptyWithoutQuery() {
        when(enterpriseQualificationMapper.selectList(any())).thenReturn(List.of());
        when(qualificationSafetyPermitMapper.selectList(any())).thenReturn(List.of());

        QualificationAlertPageReqVO reqVO = new QualificationAlertPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationAlertRespVO> result = service.getAlertPage(reqVO);

        assertEquals(0L, result.getTotal());
        verify(certAlertRecordMapper, never()).selectPage(
                org.mockito.ArgumentMatchers.<Page<CertAlertRecordDO>>any(), any());
    }

    @Test
    void getAlertPage_ok_mapsRefAndHandleState() {
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.EXPIRING_SOON);
        qual.setId(9001L);
        when(enterpriseQualificationMapper.selectList(any())).thenReturn(List.of(qual));
        when(qualificationSafetyPermitMapper.selectList(any())).thenReturn(List.of());
        CertAlertRecordDO alert = new CertAlertRecordDO();
        alert.setId(4001L);
        alert.setRefType("QUAL");
        alert.setRefId(9001L);
        alert.setAlertType(CertificateAlertTypeEnum.QUAL_EXPIRY.name());
        alert.setAlertLevel("WARNING");
        alert.setHandled(Boolean.FALSE);
        Page<CertAlertRecordDO> page = new Page<>(1, 10);
        page.setRecords(List.of(alert));
        page.setTotal(1L);
        when(certAlertRecordMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<CertAlertRecordDO>>any(), any())).thenReturn(page);

        QualificationAlertPageReqVO reqVO = new QualificationAlertPageReqVO();
        reqVO.setEntId(1L);
        reqVO.setHandled(Boolean.FALSE);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<QualificationAlertRespVO> result = service.getAlertPage(reqVO);

        assertEquals("QUAL", result.getList().get(0).getRefType());
        assertEquals(9001L, result.getList().get(0).getRefId().longValue());
        assertEquals(Boolean.FALSE, result.getList().get(0).getHandled());
    }

    // ========== 工具 ==========

    private EnterpriseQualificationSaveReqVO req(Long id) {
        EnterpriseQualificationSaveReqVO r = new EnterpriseQualificationSaveReqVO();
        r.setId(id);
        r.setEntId(1L);
        r.setQualSerial("CONSTRUCTION");
        r.setQualCategory("市政公用工程施工总承包");
        r.setQualGrade("一级");
        r.setCertNo("D123456789");
        r.setIssuingAuthority("××省住房和城乡建设厅");
        r.setValidTo(LocalDate.of(2029, 6, 30));
        return r;
    }

    private EnterpriseQualificationDO qualOf(QualificationStatusEnum status) {
        EnterpriseQualificationDO q = new EnterpriseQualificationDO();
        q.setId(1L);
        q.setEntId(1L);
        q.setQualSerial("CONSTRUCTION");
        q.setCertNo("D123456789");
        q.setQualStatus(status.name());
        return q;
    }

    private QualificationApplyRecordDO applyOf(Long id, QualificationApplyStatusEnum status) {
        QualificationApplyRecordDO a = new QualificationApplyRecordDO();
        a.setId(id);
        a.setEntId(1L);
        a.setQualId(1L);
        a.setApplyType(QualificationApplyTypeEnum.RENEW.name());
        a.setStatus(status.name());
        return a;
    }
}
