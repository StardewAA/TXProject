package cn.iocoder.txgy.module.crm.service.followup;

import cn.iocoder.txgy.module.crm.controller.admin.followup.vo.CrmFollowUpRecordSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.followup.CrmFollowUpRecordDO;
import cn.iocoder.txgy.module.crm.dal.mysql.followup.CrmFollowUpRecordMapper;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.txgy.module.crm.service.CrmServiceTestSupport;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.FOLLOW_UP_RECORD_TIME_FUTURE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CrmFollowUpRecordServiceImpl} 的单元测试
 *
 * 覆盖「三个时间字段」的口径（这是本次缺陷的核心）：
 *   1. createTime   ：跟进笔记录入系统的时间（写笔记的那一刻）
 *   2. followUpTime ：本次实际联系客户的时间，是「多久没跟进 → 掉入公海」的计时起点，允许补录历史
 *   3. nextTime     ：下次联系时间，业务计划时间，仅用于【待办】提醒，不参与公海计时
 *
 * @author OPENLAB BS
 */
public class CrmFollowUpRecordServiceImplTest extends CrmServiceTestSupport {

    @Resource
    private CrmFollowUpRecordService followUpRecordService;
    @Resource
    private CrmCustomerService customerService;
    @Resource
    private CrmFollowUpRecordMapper followUpRecordMapper;

    @Test
    public void testCreateFollowUpRecord_backdatedFollowUpTime() {
        // 准备数据：一个从未跟进过的客户
        CrmCustomerDO customer = insertCustomer(o -> {});
        // 准备参数：实际是 16 天前联系的，今天才补录到系统里
        LocalDateTime followUpTime = now().minusDays(16);
        LocalDateTime nextTime = now().plusDays(3);

        // 调用
        Long recordId = followUpRecordService.createFollowUpRecord(buildReqVO(customer.getId(), followUpTime, nextTime));

        // 断言一：跟进记录上「实际跟进时间」与「创建时间（录入时间）」是两个不同的时间
        CrmFollowUpRecordDO record = followUpRecordMapper.selectById(recordId);
        assertEquals(followUpTime, record.getFollowUpTime());
        assertTrue(record.getCreateTime().isAfter(followUpTime),
                "创建时间应该是补录时的当前时间，而不是实际跟进时间");

        // 断言二：客户的「最后跟进时间」= 实际跟进时间（修复前这里被错误写成了录入时间 = 今天）
        CrmCustomerDO dbCustomer = customerMapper.selectById(customer.getId());
        assertEquals(followUpTime, dbCustomer.getContactLastTime());
        assertEquals(nextTime, dbCustomer.getContactNextTime());
        assertTrue(dbCustomer.getFollowUpStatus());
    }

    @Test
    public void testCreateFollowUpRecord_contactLastTimeNotMoveBackward() {
        // 准备数据：客户 3 天前刚被跟进过
        LocalDateTime recentTime = now().minusDays(3);
        CrmCustomerDO customer = insertCustomer(o -> o.setContactLastTime(recentTime));

        // 调用：再补录一条 16 天前的历史跟进
        followUpRecordService.createFollowUpRecord(buildReqVO(customer.getId(), now().minusDays(16), now().plusDays(1)));

        // 断言：最后跟进时间不能被「往回拨」，否则公海计时会被错误重置
        assertEquals(recentTime, customerMapper.selectById(customer.getId()).getContactLastTime());
    }

    @Test
    public void testCreateFollowUpRecord_followUpTimeDefaultNow() {
        // 准备数据
        CrmCustomerDO customer = insertCustomer(o -> {});
        LocalDateTime before = now();

        // 调用：不传实际跟进时间（兼容老版本前端）
        Long recordId = followUpRecordService.createFollowUpRecord(buildReqVO(customer.getId(), null, now().plusDays(1)));

        // 断言：跟进记录与客户的最后跟进时间，都默认取当前时间
        CrmFollowUpRecordDO record = followUpRecordMapper.selectById(recordId);
        assertNotNull(record.getFollowUpTime());
        assertTrue(Duration.between(before, record.getFollowUpTime()).abs().toMinutes() < 1);
        CrmCustomerDO dbCustomer = customerMapper.selectById(customer.getId());
        assertNotNull(dbCustomer.getContactLastTime());
        assertTrue(Duration.between(before, dbCustomer.getContactLastTime()).abs().toMinutes() < 1);
    }

    @Test
    public void testCreateFollowUpRecord_nextTimeNotUsedAsFollowUpTime() {
        // 准备数据：客户从未跟进过
        CrmCustomerDO customer = insertCustomer(o -> {});
        // 准备参数：下次联系时间填了 16 天前（计划已逾期），但本次实际跟进时间是今天
        LocalDateTime nextTime = now().minusDays(16);
        LocalDateTime followUpTime = now();
        insertPoolConfig(15, 30);

        // 调用
        followUpRecordService.createFollowUpRecord(buildReqVO(customer.getId(), followUpTime, nextTime));

        // 断言一：最后跟进时间取「实际跟进时间」，而不是下次联系时间
        CrmCustomerDO dbCustomer = customerMapper.selectById(customer.getId());
        assertEquals(followUpTime, dbCustomer.getContactLastTime());
        assertEquals(nextTime, dbCustomer.getContactNextTime());
        // 断言二：因此该客户不会因为「下次联系时间已逾期」而被回收
        assertEquals(0, customerService.autoPutCustomerPool());
    }

    @Test
    public void testCreateFollowUpRecord_followUpTimeFuture() {
        // 准备数据
        CrmCustomerDO customer = insertCustomer(o -> {});

        // 调用，并断言异常：实际跟进时间不能晚于当前时间
        assertServiceException(
                () -> followUpRecordService.createFollowUpRecord(buildReqVO(customer.getId(), now().plusDays(1), now().plusDays(2))),
                FOLLOW_UP_RECORD_TIME_FUTURE);

        // 断言：跟进记录没有落库，客户信息也没有被改动
        assertEquals(0, followUpRecordMapper.selectCount(null));
        CrmCustomerDO dbCustomer = customerMapper.selectById(customer.getId());
        assertNull(dbCustomer.getContactLastTime());
        assertFalse(dbCustomer.getFollowUpStatus());
    }

    private CrmFollowUpRecordSaveReqVO buildReqVO(Long customerId, LocalDateTime followUpTime, LocalDateTime nextTime) {
        return new CrmFollowUpRecordSaveReqVO()
                .setBizType(CrmBizTypeEnum.CRM_CUSTOMER.getType()).setBizId(customerId)
                .setType(1).setContent("跟进内容").setFollowUpTime(followUpTime).setNextTime(nextTime);
    }

}
