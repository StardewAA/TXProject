package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 到期窗口口径统一测试（2026-09-13）。
 *
 * <p>背景：同一件"哪些证书快到期"此前有三份实现且互相打架——预警扫描用开区间且不过滤 is_current、
 * 分页筛选用闭区间且过滤 is_current、到期清单又在 Java 内存过滤 entId。现场表现就是
 * "页面上说有 3 张要到期，预警却报了 5 条"。现统一到 {@code PersonnelCertificateMapper.applyExpiringWindow}
 * 一处定义，本测试把这个定义钉住（谁改口径，测试先红）。
 */
class PersonnelCertExpiryWindowTest {

    /** 窗口唯一定义：可用状态 + 仅当前记录 + valid_to ∈ [today, today+days]（今天到期必须在名单里） */
    @Test
    void applyExpiringWindow_singleDefinition_closedLowerBoundAndCurrentOnly() {
        LocalDate today = LocalDate.of(2026, 9, 13);
        // 纯单测没有 MyBatis-Plus 启动过程，Lambda 解析需要先登记实体的 TableInfo（否则报"can not find lambda cache"）
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                PersonnelCertificateDO.class);
        LambdaQueryWrapper<PersonnelCertificateDO> query = new LambdaQueryWrapper<>();

        PersonnelCertificateMapper.applyExpiringWindow(query, today, 30);

        String sql = query.getSqlSegment();
        // ① 状态口径：只认"可用"（VALID / EXPIRING_SOON），已过期/已吊销不进到期清单
        assertTrue(sql.contains("cert_status"), sql);
        // ② 只认当前记录：被换证替换的历史证不得触发到期告警（旧扫描实现漏了这条，属真缺陷）
        assertTrue(sql.contains("is_current"), sql);
        // ③ 下界闭区间（>= 今天）：今天到期必须出现，旧实现用 > 今天 会把"最后一天"漏掉
        assertTrue(sql.contains(">="), sql);
        assertTrue(sql.contains("<="), sql);
        String params = String.valueOf(query.getParamNameValuePairs());
        assertTrue(params.contains(today.toString()), params);
        assertTrue(params.contains(today.plusDays(30).toString()), params);
    }

    /** 到期清单：entId 下推 SQL（不再内存过滤），且与扫描共用同一窗口 */
    @Test
    void listExpiring_pushesEntIdIntoQuery_andMapsVo() {
        PersonnelCertificateMapper mapper = mock(PersonnelCertificateMapper.class);
        PersonnelCertificateServiceImpl service = new PersonnelCertificateServiceImpl();
        ReflectionTestUtils.setField(service, "personnelCertificateMapper", mapper);

        PersonnelCertificateDO cert = new PersonnelCertificateDO();
        cert.setId(7L);
        cert.setPersonId(100L);
        cert.setEntId(3L);
        cert.setCertType("REG_PRACTICE_CERT");
        cert.setCertNo("C-1");
        cert.setCertStatus("VALID");
        cert.setIsCurrent(true);
        cert.setValidTo(LocalDate.now().plusDays(10));
        when(mapper.selectExpiringCandidates(org.mockito.ArgumentMatchers.any(LocalDate.class), eq(30), eq(3L)))
                .thenReturn(List.of(cert));

        List<PersonnelCertRespVO> result = service.listExpiring(3L, 30);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getId());
        verify(mapper).selectExpiringCandidates(org.mockito.ArgumentMatchers.any(LocalDate.class), eq(30), eq(3L));
    }

    /** days<=0 走默认 30 天（保持既有对外语义） */
    @Test
    void listExpiring_nonPositiveDays_fallsBackTo30() {
        PersonnelCertificateMapper mapper = mock(PersonnelCertificateMapper.class);
        PersonnelCertificateServiceImpl service = new PersonnelCertificateServiceImpl();
        ReflectionTestUtils.setField(service, "personnelCertificateMapper", mapper);
        when(mapper.selectExpiringCandidates(org.mockito.ArgumentMatchers.any(LocalDate.class), eq(30), eq(null)))
                .thenReturn(List.of());

        service.listExpiring(null, 0);

        verify(mapper).selectExpiringCandidates(org.mockito.ArgumentMatchers.any(LocalDate.class), eq(30), eq(null));
    }
}
