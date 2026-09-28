package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal.HrmCertificateRenewalCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal.HrmCertificateRenewalRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateRenewalDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateRenewalMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateTypeMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateRenewalTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomString;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_CANCELLED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_RENEWAL_REVIEW_TIME_NOT_ALLOWED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_RENEWAL_REVIEW_TIME_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_RENEWAL_TIME_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_RENEWAL_TYPE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link HrmCertificateRenewalServiceImpl} 的单元测试类
 *
 * @author OPENLAB BS
 */
@Import({HrmCertificateRenewalServiceImpl.class, HrmCertificateLedgerServiceImpl.class,
        HrmCertificateTypeServiceImpl.class, HrmCertificateStatusCalculatorImpl.class})
public class HrmCertificateRenewalServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmCertificateRenewalServiceImpl certificateRenewalService;
    @Resource
    private HrmCertificateRenewalMapper certificateRenewalMapper;
    @Resource
    private HrmEmployeeCertificateMapper certificateMapper;
    @Resource
    private HrmCertificateTypeMapper certificateTypeMapper;

    @MockitoBean
    private HrmEmployeeService employeeService;
    @MockitoBean
    private HrmCertificateWarningService certificateWarningService;

    @Test
    public void testCreateCertificateRenewal_renewKeepsHistory() {
        // mock 数据：原证件 2020-01-01 ~ 2026-09-30
        HrmCertificateTypeDO certificateType = insertCertificateType(30);
        // 纳秒精度在 H2 中会被截断，这里统一置零，避免比较时出现精度差异
        LocalDateTime oldEndTime = LocalDateTime.now().withNano(0).plusDays(12);
        HrmEmployeeCertificateDO certificate = insertCertificate(certificateType.getId(), oldEndTime,
                HrmCertificateStatusEnum.EXPIRING.getStatus());
        // 准备参数：续证到一年后
        LocalDateTime newEndTime = oldEndTime.plusYears(1);
        HrmCertificateRenewalCreateReqVO reqVO = buildReqVO(certificate.getId(),
                HrmCertificateRenewalTypeEnum.RENEW.getType())
                .setNewNo("T44030019900718001X-2027")
                .setNewStartTime(oldEndTime.plusDays(1))
                .setNewEndTime(newEndTime)
                .setAttachmentUrl("https://file.example.com/cert-2027.pdf");

        // 调用
        Long id = certificateRenewalService.createCertificateRenewal(reqVO);

        // 断言：历史记录保留原证件快照
        assertNotNull(id);
        HrmCertificateRenewalDO renewal = certificateRenewalMapper.selectById(id);
        assertPojoEquals(reqVO, renewal, "id", "newStartTime", "newEndTime", "reviewTime", "issuingTime");
        assertEquals(certificate.getId(), renewal.getCertificateId());
        assertEquals(certificate.getEmployeeId(), renewal.getEmployeeId());
        assertEquals(certificate.getNo(), renewal.getOldNo());
        assertEquals(certificate.getStartTime(), renewal.getOldStartTime());
        assertEquals(certificate.getEndTime(), renewal.getOldEndTime());
        assertEquals(newEndTime.toLocalDate().atStartOfDay(), renewal.getNewEndTime());
        // 断言：台账更新为新值，且状态重算为有效
        HrmEmployeeCertificateDO updated = certificateMapper.selectById(certificate.getId());
        assertEquals("T44030019900718001X-2027", updated.getNo());
        assertEquals(newEndTime.toLocalDate().atStartOfDay(), updated.getEndTime());
        assertEquals("https://file.example.com/cert-2027.pdf", updated.getAttachmentUrl());
        assertEquals(HrmCertificateStatusEnum.VALID.getStatus(), updated.getStatus());
    }

    @Test
    public void testCreateCertificateRenewal_reviewWritesReviewTime() {
        // mock 数据
        HrmCertificateTypeDO certificateType = insertCertificateType(30);
        HrmEmployeeCertificateDO certificate = insertCertificate(certificateType.getId(),
                LocalDateTime.now().plusDays(20), HrmCertificateStatusEnum.EXPIRING.getStatus());
        // 准备参数：复审并写入复审日期
        LocalDateTime reviewTime = LocalDateTime.now().plusDays(30);
        HrmCertificateRenewalCreateReqVO reqVO = buildReqVO(certificate.getId(),
                HrmCertificateRenewalTypeEnum.REVIEW.getType())
                .setReviewTime(reviewTime)
                .setNewEndTime(certificate.getEndTime().plusYears(3));

        // 调用
        Long id = certificateRenewalService.createCertificateRenewal(reqVO);

        // 断言
        assertEquals(reviewTime.toLocalDate().atStartOfDay(),
                certificateRenewalMapper.selectById(id).getReviewTime());
        assertEquals(reviewTime.toLocalDate().atStartOfDay(),
                certificateMapper.selectById(certificate.getId()).getReviewTime());
    }

    @Test
    public void testCreateCertificateRenewal_reviewWithoutReviewTime() {
        // mock 数据
        HrmCertificateTypeDO certificateType = insertCertificateType(30);
        HrmEmployeeCertificateDO certificate = insertCertificate(certificateType.getId(),
                LocalDateTime.now().plusDays(20), HrmCertificateStatusEnum.EXPIRING.getStatus());
        // 准备参数：复审未填写复审日期
        HrmCertificateRenewalCreateReqVO reqVO = buildReqVO(certificate.getId(),
                HrmCertificateRenewalTypeEnum.REVIEW.getType()).setReviewTime(null);

        // 调用，并断言异常
        assertServiceException(() -> certificateRenewalService.createCertificateRenewal(reqVO),
                CERTIFICATE_RENEWAL_REVIEW_TIME_REQUIRED);
    }

    @Test
    public void testCreateCertificateRenewal_renewWithReviewTime() {
        // mock 数据
        HrmCertificateTypeDO certificateType = insertCertificateType(30);
        HrmEmployeeCertificateDO certificate = insertCertificate(certificateType.getId(),
                LocalDateTime.now().plusDays(20), HrmCertificateStatusEnum.EXPIRING.getStatus());
        // 准备参数：续证却填写了复审日期
        HrmCertificateRenewalCreateReqVO reqVO = buildReqVO(certificate.getId(),
                HrmCertificateRenewalTypeEnum.RENEW.getType()).setReviewTime(LocalDateTime.now().plusDays(30));

        // 调用，并断言异常
        assertServiceException(() -> certificateRenewalService.createCertificateRenewal(reqVO),
                CERTIFICATE_RENEWAL_REVIEW_TIME_NOT_ALLOWED);
    }

    @Test
    public void testCreateCertificateRenewal_typeInvalid() {
        // mock 数据
        HrmEmployeeCertificateDO certificate = insertCertificate(null, LocalDateTime.now().plusDays(20),
                HrmCertificateStatusEnum.EXPIRING.getStatus());
        // 准备参数：非法类型
        HrmCertificateRenewalCreateReqVO reqVO = buildReqVO(certificate.getId(), 99);

        // 调用，并断言异常
        assertServiceException(() -> certificateRenewalService.createCertificateRenewal(reqVO),
                CERTIFICATE_RENEWAL_TYPE_INVALID);
    }

    @Test
    public void testCreateCertificateRenewal_newEndTimeBeforeOld() {
        // mock 数据
        HrmEmployeeCertificateDO certificate = insertCertificate(null, LocalDateTime.now().plusDays(20),
                HrmCertificateStatusEnum.EXPIRING.getStatus());
        // 准备参数：新有效期早于原有效期
        HrmCertificateRenewalCreateReqVO reqVO = buildReqVO(certificate.getId(),
                HrmCertificateRenewalTypeEnum.RENEW.getType())
                .setNewEndTime(certificate.getEndTime().minusDays(1));

        // 调用，并断言异常
        assertServiceException(() -> certificateRenewalService.createCertificateRenewal(reqVO),
                CERTIFICATE_RENEWAL_TIME_INVALID);
    }

    @Test
    public void testCreateCertificateRenewal_cancelled() {
        // mock 数据：已注销的证件
        HrmEmployeeCertificateDO certificate = insertCertificate(null, LocalDateTime.now().plusDays(20),
                HrmCertificateStatusEnum.CANCELLED.getStatus());
        // 准备参数
        HrmCertificateRenewalCreateReqVO reqVO = buildReqVO(certificate.getId(),
                HrmCertificateRenewalTypeEnum.RENEW.getType());

        // 调用，并断言异常
        assertServiceException(() -> certificateRenewalService.createCertificateRenewal(reqVO),
                CERTIFICATE_LEDGER_CANCELLED);
    }

    @Test
    public void testCreateCertificateRenewal_certificateNotExists() {
        // 准备参数
        HrmCertificateRenewalCreateReqVO reqVO = buildReqVO(randomLongId(),
                HrmCertificateRenewalTypeEnum.RENEW.getType());

        // 调用，并断言异常
        assertServiceException(() -> certificateRenewalService.createCertificateRenewal(reqVO),
                CERTIFICATE_LEDGER_NOT_EXISTS);
    }

    @Test
    public void testGetCertificateRenewalList_orderByIdDesc() {
        // mock 数据
        HrmEmployeeCertificateDO certificate = insertCertificate(null, LocalDateTime.now().plusDays(20),
                HrmCertificateStatusEnum.EXPIRING.getStatus());
        HrmCertificateRenewalDO first = insertRenewal(certificate, HrmCertificateRenewalTypeEnum.RENEW.getType());
        HrmCertificateRenewalDO second = insertRenewal(certificate, HrmCertificateRenewalTypeEnum.REVIEW.getType());

        // 调用
        List<HrmCertificateRenewalRespVO> list =
                certificateRenewalService.getCertificateRenewalList(certificate.getId());

        // 断言：最新办理的记录排在最前
        assertEquals(2, list.size());
        assertEquals(second.getId(), list.get(0).getId());
        assertEquals(first.getId(), list.get(1).getId());
    }

    private HrmCertificateTypeDO insertCertificateType(Integer advanceDays) {
        HrmCertificateTypeDO certificateType = HrmCertificateTypeDO.builder()
                .name("特种作业操作证" + randomString())
                .code(randomString())
                .specialOperation(true)
                .needReview(true)
                .reviewCycleMonths(36)
                .advanceDays(advanceDays)
                .requireAttachment(false)
                .status(CommonStatusEnum.ENABLE.getStatus())
                .sort(10)
                .build();
        certificateTypeMapper.insert(certificateType);
        return certificateType;
    }

    private HrmEmployeeCertificateDO insertCertificate(Long typeId, LocalDateTime endTime, Integer status) {
        HrmEmployeeCertificateDO certificate = HrmEmployeeCertificateDO.builder()
                .employeeId(randomLongId())
                .typeId(typeId)
                .name("低压电工作业操作证")
                .no("T44030019900718001X")
                .startTime(LocalDateTime.now().withNano(0).minusYears(1))
                .endTime(endTime)
                .issuingAuthority("深圳市应急管理局")
                .attachmentUrl("https://file.example.com/cert.pdf")
                .status(status)
                .sort(1)
                .build();
        certificateMapper.insert(certificate);
        return certificate;
    }

    private HrmCertificateRenewalDO insertRenewal(HrmEmployeeCertificateDO certificate, Integer type) {
        HrmCertificateRenewalDO renewal = randomPojo(HrmCertificateRenewalDO.class, o -> {
            o.setId(null);
            o.setCertificateId(certificate.getId());
            o.setEmployeeId(certificate.getEmployeeId());
            o.setType(type);
            o.setOldEndTime(certificate.getEndTime());
        });
        certificateRenewalMapper.insert(renewal);
        return renewal;
    }

    private HrmCertificateRenewalCreateReqVO buildReqVO(Long certificateId, Integer type) {
        HrmCertificateRenewalCreateReqVO reqVO = randomPojo(HrmCertificateRenewalCreateReqVO.class, o -> {
            o.setCertificateId(certificateId);
            o.setType(type);
            o.setNewNo("T44030019900718001X-2027");
            o.setNewStartTime(LocalDateTime.now().plusDays(1));
            o.setNewEndTime(LocalDateTime.now().plusYears(1));
            o.setReviewTime(null);
            o.setIssuingAuthority("深圳市应急管理局");
            o.setIssuingTime(LocalDateTime.now());
            o.setAttachmentUrl("https://file.example.com/cert-2027.pdf");
        });
        return reqVO;
    }

}
