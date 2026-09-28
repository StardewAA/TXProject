package cn.iocoder.txgy.module.crm.service;

import cn.iocoder.txgy.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.txgy.framework.tenant.config.TenantProperties;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerPoolConfigDO;
import cn.iocoder.txgy.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.customer.CrmCustomerPoolConfigMapper;
import cn.iocoder.txgy.module.crm.service.business.CrmBusinessService;
import cn.iocoder.txgy.module.crm.service.clue.CrmClueService;
import cn.iocoder.txgy.module.crm.service.contact.CrmContactService;
import cn.iocoder.txgy.module.crm.service.contract.CrmContractService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerLimitConfigService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerPoolConfigServiceImpl;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerServiceImpl;
import cn.iocoder.txgy.module.crm.service.followup.CrmFollowUpRecordServiceImpl;
import cn.iocoder.txgy.module.crm.service.permission.CrmOwnerRecordService;
import cn.iocoder.txgy.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.txgy.module.crm.service.quotation.CrmQuotationService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * CRM 跟进记录 + 客户公海（自动回收）单元测试的公共基类
 *
 * 说明：
 *   1. 依赖内存 DB（H2），需要自己模块的 Mapper、其它模块的 Service 用 Mock 掉；
 *   2. 额外注册了多租户拦截器（生产环境由 TxgyTenantAutoConfiguration 提供），
 *      用于验证「CRM 的所有读写只作用于当前租户的数据」；
 *   3. 每个用例前把当前租户设置为 {@link #TENANT_ID}，模拟 Web 层 TenantContextWebFilter 的行为。
 *
 * @author OPENLAB BS
 */
@Import({CrmFollowUpRecordServiceImpl.class, CrmCustomerServiceImpl.class, CrmCustomerPoolConfigServiceImpl.class,
        CrmServiceTestSupport.TenantDatabaseTestConfiguration.class})
public abstract class CrmServiceTestSupport extends BaseDbUnitTest {

    /**
     * 测试租户编号
     */
    protected static final Long TENANT_ID = 1L;

    @Resource
    protected CrmCustomerMapper customerMapper;
    @Resource
    protected CrmCustomerPoolConfigMapper customerPoolConfigMapper;

    // ==================== 被 Mock 的依赖 ====================
    // 客户 / 跟进记录 Service 还依赖了其它业务 Service，本单测只关心「客户 + 跟进记录」两张表，
    // 因此统一用 Mock 顶上，避免把无关的真实实现拉进测试上下文

    @MockitoBean
    protected CrmOwnerRecordService ownerRecordService;
    @MockitoBean
    protected CrmPermissionService permissionService;
    @MockitoBean
    protected CrmCustomerLimitConfigService customerLimitConfigService;
    @MockitoBean
    protected CrmContactService contactService;
    @MockitoBean
    protected CrmBusinessService businessService;
    @MockitoBean
    protected CrmContractService contractService;
    @MockitoBean
    protected CrmQuotationService quotationService;
    @MockitoBean
    protected CrmClueService clueService;
    @MockitoBean
    protected AdminUserApi adminUserApi;

    @BeforeEach
    public void setUpTenantAndClock() {
        // 模拟 Web 层的行为：每次请求都会把租户编号写入上下文
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    // ==================== 通用工具方法 ====================

    /**
     * 取当前时间（去掉纳秒）
     *
     * 去纳秒的原因：H2/MySQL 的 timestamp 精度与 Java 不一致，去掉后可直接用 assertEquals 断言
     */
    protected static LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    /**
     * 插入一个客户，默认：有负责人（1 号用户）、未锁定、未成交、从未跟进
     *
     * @param consumer 需要覆盖的字段
     * @return 插入成功的客户（含自增主键）
     */
    protected CrmCustomerDO insertCustomer(Consumer<CrmCustomerDO> consumer) {
        CrmCustomerDO customer = new CrmCustomerDO().setName("测试客户")
                .setFollowUpStatus(false).setLockStatus(false).setDealStatus(false)
                .setOwnerUserId(1L).setOwnerTime(now().minusDays(1));
        consumer.accept(customer);
        customerMapper.insert(customer);
        return customer;
    }

    /**
     * 插入公海配置（默认启用）
     *
     * @param contactExpireDays 未跟进放入公海天数
     * @param dealExpireDays    未成交放入公海天数
     */
    protected CrmCustomerPoolConfigDO insertPoolConfig(Integer contactExpireDays, Integer dealExpireDays) {
        return insertPoolConfig(contactExpireDays, dealExpireDays, true);
    }

    protected CrmCustomerPoolConfigDO insertPoolConfig(Integer contactExpireDays, Integer dealExpireDays,
                                                       Boolean enabled) {
        CrmCustomerPoolConfigDO config = new CrmCustomerPoolConfigDO().setEnabled(enabled)
                .setContactExpireDays(contactExpireDays).setDealExpireDays(dealExpireDays)
                .setNotifyEnabled(false).setNotifyDays(1);
        customerPoolConfigMapper.insert(config);
        return config;
    }

    /**
     * 单测环境补齐「多租户」拦截器
     *
     * BaseDbUnitTest 只导入了数据源 + MyBatis 配置，不包含 TxgyTenantAutoConfiguration，
     * 这里手动注册，用于验证「公海回收等批量操作只处理当前租户的数据」。
     *
     * 注意：必须用 BeanPostProcessor 往「最终的」MybatisPlusInterceptor 实例上加，
     * 不能自己再声明一个 @Bean 去改（MybatisPlusInterceptor 可能被自动配置重新创建，
     * 改动会丢失）。该写法与 BiddingMybatisConfiguration 保持一致。
     */
    @Configuration(proxyBeanMethods = false)
    public static class TenantDatabaseTestConfiguration {

        @Bean
        public BeanPostProcessor tenantLineInnerInterceptorPostProcessor() {
            return new BeanPostProcessor() {

                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof MybatisPlusInterceptor interceptor) {
                        boolean exists = interceptor.getInterceptors().stream()
                                .anyMatch(inner -> inner instanceof TenantLineInnerInterceptor);
                        if (!exists) {
                            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                                    new TenantDatabaseInterceptor(new TenantProperties()));
                            // 需要加在首个，主要是为了在分页插件前面（MyBatis Plus 的硬性规定）
                            MyBatisUtils.addInterceptor(interceptor, inner, 0);
                        }
                    }
                    return bean;
                }

            };
        }

    }

}
