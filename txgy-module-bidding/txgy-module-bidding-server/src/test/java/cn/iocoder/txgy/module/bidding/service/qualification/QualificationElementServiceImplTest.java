package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationAssetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationPerformanceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationAssetMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationPerformanceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStaffConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资质要素录入薄服务冒烟测试：默认值补齐与主键回传。
 * 依据：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3/15-4/15-5。
 */
class QualificationElementServiceImplTest {

    private QualificationStaffConfigMapper staffMapper;
    private QualificationPerformanceMapper perfMapper;
    private QualificationAssetMapper assetMapper;
    private QualificationElementServiceImpl service;

    @BeforeEach
    void setUp() {
        staffMapper = mock(QualificationStaffConfigMapper.class);
        perfMapper = mock(QualificationPerformanceMapper.class);
        assetMapper = mock(QualificationAssetMapper.class);
        service = new QualificationElementServiceImpl();
        ReflectionTestUtils.setField(service, "qualificationStaffConfigMapper", staffMapper);
        ReflectionTestUtils.setField(service, "qualificationPerformanceMapper", perfMapper);
        ReflectionTestUtils.setField(service, "qualificationAssetMapper", assetMapper);
    }

    @Test
    void saveStaff_fillsFullTimeDefault() {
        when(staffMapper.insert(any(QualificationStaffConfigDO.class))).thenAnswer(inv -> {
            QualificationStaffConfigDO s = inv.getArgument(0);
            s.setId(21L);
            return 1;
        });
        QualificationStaffConfigDO staff = new QualificationStaffConfigDO();
        staff.setEntId(1L);
        staff.setQualId(2L);
        staff.setStaffRole("REGISTERED_BUILDER");
        staff.setPersonName("王工");
        Long id = service.saveStaff(staff);
        assertEquals(21L, id);
        ArgumentCaptor<QualificationStaffConfigDO> cap = ArgumentCaptor.forClass(QualificationStaffConfigDO.class);
        verify(staffMapper).insert(cap.capture());
        assertTrue(cap.getValue().getIsFullTime(), "在职默认 TRUE（挂靠核查口径）");
    }

    @Test
    void savePerformance_fillsPlatformVerifiedFalse() {
        when(perfMapper.insert(any(QualificationPerformanceDO.class))).thenAnswer(inv -> {
            QualificationPerformanceDO p = inv.getArgument(0);
            p.setId(22L);
            return 1;
        });
        QualificationPerformanceDO perf = new QualificationPerformanceDO();
        perf.setEntId(1L);
        perf.setQualId(2L);
        perf.setProjectName("××市市政道路工程");
        perf.setCompletionDate(LocalDate.of(2025, 8, 1));
        perf.setContractAmount(new BigDecimal("58000000"));
        Long id = service.savePerformance(perf);
        assertEquals(22L, id);
        ArgumentCaptor<QualificationPerformanceDO> cap = ArgumentCaptor.forClass(QualificationPerformanceDO.class);
        verify(perfMapper).insert(cap.capture());
        assertEquals(Boolean.FALSE, cap.getValue().getIsPlatformVerified(), "四库核验默认 FALSE");
    }

    @Test
    void saveAsset_ok() {
        when(assetMapper.insert(any(QualificationAssetDO.class))).thenAnswer(inv -> {
            QualificationAssetDO a = inv.getArgument(0);
            a.setId(23L);
            return 1;
        });
        QualificationAssetDO asset = new QualificationAssetDO();
        asset.setEntId(1L);
        asset.setAssetType("NET_ASSET");
        asset.setName("2025 年度审计净资产");
        asset.setValueAmount(new BigDecimal("86000000"));
        asset.setStatYear((short) 2025);
        assertEquals(23L, service.saveAsset(asset));
    }
}
