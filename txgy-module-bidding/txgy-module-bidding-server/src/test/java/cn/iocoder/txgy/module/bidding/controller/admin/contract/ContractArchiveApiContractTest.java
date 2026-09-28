package cn.iocoder.txgy.module.bidding.controller.admin.contract;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.contract.ContractDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.contract.ContractMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.contract.ContractRetentionWarnEnum;
import cn.iocoder.txgy.module.bidding.enums.contract.ContractStatusEnum;
import cn.iocoder.txgy.module.bidding.service.contract.ContractServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **存档 15 年归档锁契约测试（standalone，不依赖 Spring 容器/DB）**：
 * 依据《电子招标投标全流程说明书》§11.3 第 5 条（归档后只读锁定）、§12.2 第 3 条（法定时限自动计算）、
 * §14 附录（存档 ≥15 年，电子办法技术规范）。校验：
 * ① 归档接口存在且权限码 bidding:contract:archive；
 * ② 归档落 archive_time + retention_until = 归档 + 15 年（自动计算）；
 * ③ 归档幂等（重复归档不报错）；
 * ④ 归档锁：ARCHIVED 后 save 被拒（CONTRACT_ARCHIVED_LOCKED）；
 * ⑤ 非 SIGNED 状态不可归档（CONTRACT_ARCHIVE_STATUS_INVALID）；
 * ⑥ retentionWarn 自动计算（临期 EXPIRING_SOON / 已到期 EXPIRED）。
 */
class ContractArchiveApiContractTest {

    private ContractMapper contractMapper;
    private ContractServiceImpl service;
    private ContractController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        contractMapper = mock(ContractMapper.class);
        service = new ContractServiceImpl();
        ReflectionTestUtils.setField(service, "contractMapper", contractMapper);
        controller = new ContractController();
        ReflectionTestUtils.setField(controller, "contractService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ContractDO archivedContract(long id) {
        ContractDO c = new ContractDO();
        c.setId(id);
        c.setContractCode("C-2026-0001");
        c.setContractName("XX市政工程合同");
        c.setContractPrice(new BigDecimal("5800000.00"));
        c.setStatus(ContractStatusEnum.ARCHIVED.name());
        LocalDateTime archiveTime = LocalDateTime.of(2026, 9, 28, 10, 0);
        c.setArchiveTime(archiveTime);
        c.setRetentionUntil(archiveTime.plusYears(15));
        c.setRetentionWarn(ContractRetentionWarnEnum.NORMAL.name());
        return c;
    }

    @Test
    @DisplayName("归档接口存在且鉴权 bidding:contract:archive")
    void archiveEndpoint_hasPermission() {
        Method m = Arrays.stream(ContractController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals("archive")).findFirst()
                .orElseThrow(() -> new AssertionError("控制器缺少 archive 方法"));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, "archive 缺少 @PreAuthorize");
        assertTrue(a.value().contains("bidding:contract:archive"),
                "archive 鉴权应含 bidding:contract:archive，实际：" + a.value());
    }

    @Test
    @DisplayName("归档：落 archive_time + retention_until=归档+15年（自动计算），返回成功")
    void archive_setsRetention15Years() throws Exception {
        ContractDO signed = new ContractDO();
        signed.setId(1001L);
        signed.setContractCode("C-2026-0001");
        signed.setStatus(ContractStatusEnum.SIGNED.name());
        when(contractMapper.selectById(1001L)).thenReturn(signed);

        mockMvc.perform(post("/bidding/contract/archive").param("id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
        // 校验 update 内容
        org.mockito.ArgumentCaptor<ContractDO> captor = org.mockito.ArgumentCaptor.forClass(ContractDO.class);
        verify(contractMapper).updateById(captor.capture());
        ContractDO upd = captor.getValue();
        assertEquals(ContractStatusEnum.ARCHIVED.name(), upd.getStatus(), "归档后状态应=ARCHIVED");
        assertNotNull(upd.getArchiveTime(), "归档时间应落库");
        assertNotNull(upd.getRetentionUntil(), "保存到期应落库");
        assertEquals(15L, ChronoUnit.YEARS.between(upd.getArchiveTime(), upd.getRetentionUntil()),
                "保存期限必须=归档+15年（电子办法技术规范 存档≥15年）");
        assertEquals(ContractRetentionWarnEnum.NORMAL.name(), upd.getRetentionWarn(), "归档时提醒标记=NORMAL");
    }

    @Test
    @DisplayName("归档幂等：已归档再次归档不报错、不再写库")
    void archive_idempotent() throws Exception {
        ContractDO archived = archivedContract(2001L);
        when(contractMapper.selectById(2001L)).thenReturn(archived);

        mockMvc.perform(post("/bidding/contract/archive").param("id", "2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(contractMapper, never()).updateById(any(ContractDO.class));
    }

    @Test
    @DisplayName("归档锁：ARCHIVED 后 save 修改被拒（CONTRACT_ARCHIVED_LOCKED）")
    void save_onArchived_rejected() {
        // 覆盖场景：登记接口带 status=ARCHIVED 必须拒绝（归档走专用接口）
        ContractSaveReqVO req = new ContractSaveReqVO();
        req.setTenderProjectCode("I6101002026000001001");
        req.setContractCode("C-2026-0002");
        req.setContractPrice(new BigDecimal("1000000.00"));
        req.setStatus(ContractStatusEnum.ARCHIVED.name());
        Exception ex = assertThrows(Exception.class, () -> service.save(req),
                "save 直接带 ARCHIVED 应被拒（归档必须走 archive 接口落保存到期）");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("归档状态不允许"),
                "应抛 CONTRACT_ARCHIVE_STATUS_INVALID，实际：" + ex.getMessage());
        verify(contractMapper, never()).insert(any(ContractDO.class));
    }

    @Test
    @DisplayName("非 SIGNED 状态不可归档（CONTRACT_ARCHIVE_STATUS_INVALID）")
    void archive_invalidStatus_rejected() {
        ContractDO weird = new ContractDO();
        weird.setId(3001L);
        weird.setStatus("UNKNOWN");
        when(contractMapper.selectById(3001L)).thenReturn(weird);
        Exception ex = assertThrows(Exception.class, () -> service.archive(3001L));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("归档状态不允许"),
                "非 SIGNED 归档应被拒，实际：" + ex.getMessage());
        verify(contractMapper, never()).updateById(any(ContractDO.class));
    }

    @Test
    @DisplayName("合同不存在归档报 CONTRACT_NOT_EXISTS")
    void archive_notExists() {
        when(contractMapper.selectById(9999L)).thenReturn(null);
        Exception ex = assertThrows(Exception.class, () -> service.archive(9999L));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("合同不存在"),
                "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("retentionWarn 自动计算：已到期→EXPIRED，临期（≤90天）→EXPIRING_SOON")
    void retentionWarn_autoCompute() {
        ContractDO expired = archivedContract(4001L);
        expired.setRetentionUntil(LocalDateTime.now().minusDays(1));
        expired.setRetentionWarn(ContractRetentionWarnEnum.NORMAL.name());
        service.refreshRetentionWarn(expired);
        assertEquals(ContractRetentionWarnEnum.EXPIRED.name(), expired.getRetentionWarn(),
                "超过保存到期日应标 EXPIRED");

        ContractDO soon = archivedContract(4002L);
        soon.setRetentionUntil(LocalDateTime.now().plusDays(30));
        soon.setRetentionWarn(ContractRetentionWarnEnum.NORMAL.name());
        service.refreshRetentionWarn(soon);
        assertEquals(ContractRetentionWarnEnum.EXPIRING_SOON.name(), soon.getRetentionWarn(),
                "距到期 ≤90 天应标 EXPIRING_SOON");

        ContractDO normal = archivedContract(4003L);
        normal.setRetentionUntil(LocalDateTime.now().plusYears(5));
        normal.setRetentionWarn(ContractRetentionWarnEnum.NORMAL.name());
        service.refreshRetentionWarn(normal);
        assertEquals(ContractRetentionWarnEnum.NORMAL.name(), normal.getRetentionWarn(),
                "距到期 >90 天保持 NORMAL");
        // 非归档行不参与计算
        ContractDO signed = new ContractDO();
        signed.setId(4004L);
        signed.setStatus(ContractStatusEnum.SIGNED.name());
        signed.setRetentionWarn(null);
        service.refreshRetentionWarn(signed);
        assertNull(signed.getRetentionWarn(), "非归档行不参与提醒计算");
    }

    @Test
    @DisplayName("契约：ContractRespVO 外发字段含归档三列（archiveTime/retentionUntil/retentionWarn）")
    void respVO_containsArchiveFields() {
        Set<String> fields = new HashSet<>();
        for (java.lang.reflect.Field f : cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractRespVO.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                fields.add(f.getName());
            }
        }
        assertTrue(fields.contains("archiveTime"), "RespVO 应含 archiveTime");
        assertTrue(fields.contains("retentionUntil"), "RespVO 应含 retentionUntil");
        assertTrue(fields.contains("retentionWarn"), "RespVO 应含 retentionWarn");
    }

    @Test
    @DisplayName("归档锁错误码已注册（103-004/103-005）")
    void errorCodes_registered() {
        assertEquals(1_060_103_004, ErrorCodeConstants.CONTRACT_ARCHIVED_LOCKED.getCode(), "归档锁错误码=1-060-103-004");
        assertEquals(1_060_103_005, ErrorCodeConstants.CONTRACT_ARCHIVE_STATUS_INVALID.getCode(), "归档状态错误码=1-060-103-005");
    }
}
