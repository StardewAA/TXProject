package cn.iocoder.txgy.module.hrm.service.contract;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractSignRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.contract.HrmContractSignRecordMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeContractMapper;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignWayEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractStatusEnum;
import cn.iocoder.txgy.module.hrm.service.company.HrmCompanyProfileService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_SIGN_EMPLOYEE_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * {@link HrmContractSignRecordServiceImpl} 的单元测试类
 *
 * @author OPENLAB BS
 */
@Import(HrmContractSignRecordServiceImpl.class)
public class HrmContractSignRecordServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmContractSignRecordServiceImpl signRecordService;

    @Resource
    private HrmContractSignRecordMapper signRecordMapper;
    @Resource
    private HrmEmployeeContractMapper contractMapper;

    @MockitoBean
    private HrmEmployeeService employeeService;
    @MockitoBean
    private HrmCompanyProfileService companyProfileService;
    @MockitoBean
    private NotifyMessageSendApi notifyMessageSendApi;

    @Test
    public void testCreatePendingSignRecordIfAbsent_success() {
        // mock 数据：一份已绑定员工的用工合同
        Long employeeId = randomLongId();
        HrmEmployeeContractDO contract = insertContract(employeeId);
        when(employeeService.validateEmployeeExists(employeeId))
                .thenReturn(new HrmEmployeeDO().setId(employeeId).setName("张三"));

        // 调用
        Long id = signRecordService.createPendingSignRecordIfAbsent(contract.getId());

        // 断言：自动登记出一条「线下签署 + 待签」记录，台账里能看到这份合同
        assertNotNull(id);
        HrmContractSignRecordDO record = signRecordMapper.selectById(id);
        assertNotNull(record);
        assertEquals(contract.getId(), record.getContractId());
        assertEquals(employeeId, record.getEmployeeId());
        assertEquals(contract.getNo(), record.getContractNo());
        assertEquals("张三", record.getSignPerson());
        assertEquals(HrmContractSignWayEnum.OFFLINE.getWay(), record.getSignWay());
        assertEquals(HrmContractSignStatusEnum.PENDING.getStatus(), record.getStatus());
        assertEquals(0, record.getUrgeCount());
        assertEquals(contract.getSignCompany(), record.getSignEmployerName());
    }

    @Test
    public void testCreatePendingSignRecordIfAbsent_idempotent() {
        // mock 数据
        Long employeeId = randomLongId();
        HrmEmployeeContractDO contract = insertContract(employeeId);
        when(employeeService.validateEmployeeExists(employeeId))
                .thenReturn(new HrmEmployeeDO().setId(employeeId).setName("李四"));

        // 调用两次：转员工后重复发送通知不应产生多条待签记录
        Long firstId = signRecordService.createPendingSignRecordIfAbsent(contract.getId());
        Long secondId = signRecordService.createPendingSignRecordIfAbsent(contract.getId());

        // 断言
        assertEquals(firstId, secondId);
        List<HrmContractSignRecordDO> records = signRecordMapper.selectListByContractId(contract.getId());
        assertEquals(1, records.size());
    }

    @Test
    public void testCreatePendingSignRecordIfAbsent_employeeRequired() {
        // mock 数据：合同还没绑定员工（发 Offer 后、转员工前）
        HrmEmployeeContractDO contract = new HrmEmployeeContractDO().setEmployeeId(null)
                .setNo("HT-TEST-NO-EMPLOYEE").setStatus(HrmEmployeeContractStatusEnum.NOT_PERFORMED.getStatus())
                .setExpireRemind(true).setSort(0);
        contractMapper.insert(contract);

        // 调用，并断言异常
        assertServiceException(() -> signRecordService.createPendingSignRecordIfAbsent(contract.getId()),
                CONTRACT_SIGN_EMPLOYEE_REQUIRED);
    }

    private HrmEmployeeContractDO insertContract(Long employeeId) {
        HrmEmployeeContractDO contract = new HrmEmployeeContractDO().setEmployeeId(employeeId)
                .setNo("HT-TEST-" + employeeId)
                .setStatus(HrmEmployeeContractStatusEnum.NOT_PERFORMED.getStatus())
                .setStartTime(LocalDateTime.of(2026, 9, 1, 0, 0))
                .setSignCompany("西安鸥鹏互联科技有限公司")
                .setExpireRemind(true)
                .setSort(0);
        contractMapper.insert(contract);
        return contract;
    }

}
