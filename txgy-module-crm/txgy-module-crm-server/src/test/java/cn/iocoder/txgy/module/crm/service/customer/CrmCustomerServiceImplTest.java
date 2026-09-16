package cn.iocoder.txgy.module.crm.service.customer;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerPoolConfigDO;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.txgy.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.txgy.module.crm.service.CrmServiceTestSupport;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * {@link CrmCustomerServiceImpl} 的单元测试（客户公海自动回收）
 *
 * 业务规则：客户「多久没被跟进」以【最后一次实际跟进客户的时间】（crm_customer.contact_last_time）为起点，
 *          超过配置的阈值后自动掉入公海。
 *
 * @author OPENLAB BS
 */
public class CrmCustomerServiceImplTest extends CrmServiceTestSupport {

    @Resource
    private CrmCustomerService customerService;

    @Test
    public void testAutoPutCustomerPool_notFollowUpExpired() {
        // 准备数据：15 天没跟进就回收
        insertPoolConfig(15, 30);
        // 客户一：16 天没跟进 → 应该回收
        CrmCustomerDO expired = insertCustomer(o -> o.setOwnerTime(now().minusDays(30))
                .setContactLastTime(now().minusDays(16)));
        // 客户二：3 天前刚跟进过，且成为负责人也不久 → 不回收
        CrmCustomerDO followed = insertCustomer(o -> o.setOwnerTime(now().minusDays(10))
                .setContactLastTime(now().minusDays(3)));
        // 客户三：从未跟进，但成为负责人只有 3 天（宽限期内）→ 不回收
        CrmCustomerDO fresh = insertCustomer(o -> o.setOwnerTime(now().minusDays(3)).setContactLastTime(null));

        // 调用
        Integer count = customerService.autoPutCustomerPool();

        // 断言一：只有「超时未跟进」的客户掉入公海（负责人的用户编号被清空）
        assertEquals(1, count);
        assertNull(customerMapper.selectById(expired.getId()).getOwnerUserId());
        assertEquals(1L, customerMapper.selectById(followed.getId()).getOwnerUserId());
        assertEquals(1L, customerMapper.selectById(fresh.getId()).getOwnerUserId());
        // 断言二：写入负责人变更历史（预负责人 = 原负责人，新负责人 = 空）
        verify(ownerRecordService).createOwnerRecord(argThat(bo -> expired.getId().equals(bo.getBizId())
                && Long.valueOf(1L).equals(bo.getPreOwnerUserId()) && bo.getPostOwnerUserId() == null));
        // 断言三：删除该客户的负责人数据权限
        verify(permissionService).deletePermission(CrmBizTypeEnum.CRM_CUSTOMER.getType(), expired.getId(),
                CrmPermissionLevelEnum.OWNER.getLevel());
        // 断言四：客户的联系人负责人也要一并清空
        verify(contactService).updateOwnerUserIdByCustomerIdIgnorePermission(expired.getId(), null);
    }

    @Test
    public void testAutoPutCustomerPool_dealExpired() {
        // 准备数据：30 天没成交就回收；客户 31 天前成为负责人，但 3 天前刚跟进过
        insertPoolConfig(15, 30);
        CrmCustomerDO customer = insertCustomer(o -> o.setOwnerTime(now().minusDays(31))
                .setContactLastTime(now().minusDays(3)));

        // 调用
        Integer count = customerService.autoPutCustomerPool();

        // 断言：命中「未成交超时」规则，同样回收
        assertEquals(1, count);
        assertNull(customerMapper.selectById(customer.getId()).getOwnerUserId());
    }

    @Test
    public void testAutoPutCustomerPool_skipDealLockedAndInPool() {
        // 准备数据
        insertPoolConfig(15, 30);
        // 客户一：已成交
        CrmCustomerDO deal = insertCustomer(o -> o.setOwnerTime(now().minusDays(30))
                .setContactLastTime(now().minusDays(30)).setDealStatus(true));
        // 客户二：已锁定
        CrmCustomerDO locked = insertCustomer(o -> o.setOwnerTime(now().minusDays(30))
                .setContactLastTime(now().minusDays(30)).setLockStatus(true));
        // 客户三：已经在公海里（没有负责人）
        CrmCustomerDO inPool = insertCustomer(o -> o.setOwnerTime(now().minusDays(30))
                .setContactLastTime(now().minusDays(30)).setOwnerUserId(null));

        // 调用
        Integer count = customerService.autoPutCustomerPool();

        // 断言：以上三种都不回收
        assertEquals(0, count);
        assertEquals(1L, customerMapper.selectById(deal.getId()).getOwnerUserId());
        assertEquals(1L, customerMapper.selectById(locked.getId()).getOwnerUserId());
        assertNull(customerMapper.selectById(inPool.getId()).getOwnerUserId());
        verify(ownerRecordService, never()).createOwnerRecord(any());
    }

    @Test
    public void testAutoPutCustomerPool_disabled() {
        // 准备数据：公海未启用
        insertPoolConfig(15, 30, false);
        CrmCustomerDO customer = insertCustomer(o -> o.setOwnerTime(now().minusDays(30))
                .setContactLastTime(now().minusDays(30)));

        // 调用
        Integer count = customerService.autoPutCustomerPool();

        // 断言：不执行回收
        assertEquals(0, count);
        assertEquals(1L, customerMapper.selectById(customer.getId()).getOwnerUserId());
    }

    @Test
    public void testAutoPutCustomerPool_noExpireDays() {
        // 准备数据：历史脏数据，未配置「未跟进天数 / 未成交天数」
        customerPoolConfigMapper.insert(new CrmCustomerPoolConfigDO().setEnabled(true)
                .setContactExpireDays(null).setDealExpireDays(null).setNotifyEnabled(false));
        CrmCustomerDO customer = insertCustomer(o -> o.setOwnerTime(now().minusDays(30))
                .setContactLastTime(now().minusDays(30)));

        // 调用
        Integer count = customerService.autoPutCustomerPool();

        // 断言：直接跳过（不能抛 NPE 导致整个定时任务失败）
        assertEquals(0, count);
        assertEquals(1L, customerMapper.selectById(customer.getId()).getOwnerUserId());
    }

    @Test
    public void testAutoPutCustomerPool_tenantIsolation() {
        // 准备数据：租户 1 和租户 2 各有一个「31 天没跟进」的客户
        Long tenant1CustomerId = TenantUtils.execute(1L, () -> {
            insertPoolConfig(15, 30);
            return insertCustomer(o -> o.setOwnerTime(now().minusDays(40))
                    .setContactLastTime(now().minusDays(31))).getId();
        });
        Long tenant2CustomerId = TenantUtils.execute(2L, () -> {
            insertPoolConfig(15, 30);
            return insertCustomer(o -> o.setOwnerTime(now().minusDays(40))
                    .setContactLastTime(now().minusDays(31))).getId();
        });

        // 调用：只在租户 1 的上下文里执行回收（定时任务里 @TenantJob 会按租户逐个执行）
        Integer count = TenantUtils.execute(1L, () -> {
            return customerService.autoPutCustomerPool();
        });

        // 断言：只回收当前租户的数据，租户 2 的数据不能被误伤
        assertEquals(1, count);
        assertNull(TenantUtils.execute(1L, () -> customerMapper.selectById(tenant1CustomerId).getOwnerUserId()));
        assertEquals(1L, TenantUtils.execute(2L, () -> customerMapper.selectById(tenant2CustomerId).getOwnerUserId()));
    }

}
