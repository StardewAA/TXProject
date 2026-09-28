package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertChangeLogPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertChangeLogRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertChangeLogDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;
import cn.iocoder.txgy.module.bidding.enums.certificate.CertDictHelper;
import cn.iocoder.txgy.module.bidding.enums.certificate.CertDictTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 证照变更留痕读出口单测（2026-09-21 补：该表此前有写入、无查询接口）。
 */
class CertificateChangeLogServiceImplTest {

    private CertChangeLogMapper certChangeLogMapper;
    private CertDictHelper certDictHelper;
    private CertificateServiceImpl service;

    @BeforeEach
    void setUp() {
        certChangeLogMapper = mock(CertChangeLogMapper.class);
        certDictHelper = mock(CertDictHelper.class);
        service = new CertificateServiceImpl();
        ReflectionTestUtils.setField(service, "certChangeLogMapper", certChangeLogMapper);
        ReflectionTestUtils.setField(service, "certDictHelper", certDictHelper);
        // 字典可用：changeType/opSource 解析出中文名
        org.mockito.Mockito.lenient().when(certDictHelper.labelMap(CertDictTypes.CHANGE_TYPE))
                .thenReturn(Map.of("STATUS_CHANGE", "状态流转", "CREATE", "建档"));
        org.mockito.Mockito.lenient().when(certDictHelper.labelMap(CertDictTypes.OP_SOURCE))
                .thenReturn(Map.of("MANUAL", "手工操作"));
    }

    private static CertChangeLogDO sample() {
        CertChangeLogDO d = new CertChangeLogDO();
        d.setId(86L);
        d.setCertId(21L);
        d.setFieldName("cert_status");
        d.setOldValue("ACTIVE");
        d.setNewValue("EXPIRED");
        d.setChangeType("STATUS_CHANGE");
        d.setOpSource("MANUAL");
        d.setOpIp("127.0.0.1");
        d.setBatchId(null);
        d.setRecordHash("94064de399b9");
        d.setPrevHash("bf4f7a488e53");
        d.setCreator("1");
        d.setCreateTime(LocalDateTime.of(2026, 9, 20, 11, 30, 1));
        return d;
    }

    @Test
    @DisplayName("留痕分页：字段映射完整，中文名由字典解析，哈希链凭证保留下发")
    void changeLogPage_mapsAllFields() {
        when(certChangeLogMapper.selectPage(any(CertChangeLogPageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(sample()), 1L));

        CertChangeLogPageReqVO req = new CertChangeLogPageReqVO();
        req.setCertId(21L);
        PageResult<CertChangeLogRespVO> page = service.getChangeLogPage(req);

        assertEquals(1L, page.getTotal());
        CertChangeLogRespVO vo = page.getList().get(0);
        assertEquals(86L, vo.getId());
        assertEquals(21L, vo.getCertId());
        assertEquals("cert_status", vo.getFieldName());
        assertEquals("ACTIVE", vo.getOldValue());
        assertEquals("EXPIRED", vo.getNewValue());
        assertEquals("STATUS_CHANGE", vo.getChangeType());
        assertEquals("状态流转", vo.getChangeTypeName(), "中文名由字典解析");
        assertEquals("MANUAL", vo.getOpSource());
        assertEquals("手工操作", vo.getOpSourceName());
        assertEquals("1", vo.getOperator(), "操作人取审计字段 creator");
        // 哈希链凭证必须下发：前端做"留痕未被篡改"展示依赖它
        assertEquals("94064de399b9", vo.getRecordHash());
        assertEquals("bf4f7a488e53", vo.getPrevHash());
    }

    @Test
    @DisplayName("留痕分页：字典不可用时中文名为 null（不抛异常，前端回退显示 code）")
    void changeLogPage_dictUnavailable_isFaultTolerant() {
        when(certChangeLogMapper.selectPage(any(CertChangeLogPageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(sample()), 1L));
        when(certDictHelper.labelMap(any())).thenReturn(Map.of());

        CertChangeLogPageReqVO req = new CertChangeLogPageReqVO();
        req.setCertId(21L);
        CertChangeLogRespVO vo = service.getChangeLogPage(req).getList().get(0);

        assertNull(vo.getChangeTypeName(), "字典缺失只影响中文名，不影响业务数据");
        assertEquals("STATUS_CHANGE", vo.getChangeType(), "code 仍照常下发");
    }

    @Test
    @DisplayName("留痕分页：空结果返回 total=0 且不抛异常")
    void changeLogPage_empty() {
        when(certChangeLogMapper.selectPage(any(CertChangeLogPageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        CertChangeLogPageReqVO req = new CertChangeLogPageReqVO();
        req.setCertId(99999L);
        PageResult<CertChangeLogRespVO> page = service.getChangeLogPage(req);

        assertEquals(0L, page.getTotal());
        assertEquals(0, page.getList().size());
    }
}
