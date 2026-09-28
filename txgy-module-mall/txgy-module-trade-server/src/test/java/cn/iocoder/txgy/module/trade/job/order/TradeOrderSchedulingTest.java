package cn.iocoder.txgy.module.trade.job.order;

import cn.iocoder.txgy.framework.quartz.config.TxgyXxlJobAutoConfiguration;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderSchedulingConfiguration;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实最小 Spring 上下文注册真实任务，调度器和业务依赖全部 mock，不访问数据库或 ERP。 */
class TradeOrderSchedulingTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-15T08:00:00Z");

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void explicitlyEnabledFallbackIsRegisteredEvenWhenXxlJobIsDisabled() {
        try (var context = context("true")) {
            assertEquals("false", context.getEnvironment().getProperty("xxl.job.enabled"));
            assertEquals("true", context.getEnvironment().getProperty("txgy.trade.order.local-auto-cancel-enabled"));
            assertFalse(context.containsBean("xxlJobExecutor"));
            var processor = context.getBean(ScheduledAnnotationBeanPostProcessor.class);
            assertEquals(1, processor.getScheduledTasks().size());
            var task = processor.getScheduledTasks().iterator().next().getTask();
            var fixedDelay = assertInstanceOf(FixedDelayTask.class, task);
            assertEquals(Duration.ofSeconds(60), fixedDelay.getIntervalDuration());
            assertEquals(Duration.ofSeconds(10), fixedDelay.getInitialDelayDuration());
            assertTrue(task.toString().contains("TradeOrderAutoCancelJob.fallbackExecute"));
            verify(context.getBean(TaskScheduler.class)).scheduleWithFixedDelay(
                    any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(60)));
            verifyNoInteractions(context.getBean(TradeOrderUpdateService.class), context.getBean(TenantFrameworkService.class));
        }
    }

    @Test
    void registeredTaskUsesEachTenantAndRestoresCallingContext() {
        try (var context = context("true")) {
            var tenants = context.getBean(TenantFrameworkService.class);
            var orders = context.getBean(TradeOrderUpdateService.class);
            when(tenants.getTenantIds()).thenReturn(List.of(1L, 3000L));
            List<Long> visited = new ArrayList<>();
            when(orders.cancelOrderBySystem()).thenAnswer(call -> {
                assertFalse(TenantContextHolder.isIgnore());
                visited.add(TenantContextHolder.getRequiredTenantId());
                return 0;
            });
            TenantContextHolder.setTenantId(91L);
            TenantContextHolder.setIgnore(true);
            var processor = context.getBean(ScheduledAnnotationBeanPostProcessor.class);
            // 执行被调度器真实注册的 Runnable，而不是直接调用一个未注册的方法。
            processor.getScheduledTasks().iterator().next().getTask().getRunnable().run();
            assertEquals(List.of(1L, 3000L), visited);
            assertEquals(91L, TenantContextHolder.getTenantId());
            assertTrue(TenantContextHolder.isIgnore());
            verify(orders, times(2)).cancelOrderBySystem();
        }
    }

    @Test
    void fallbackIsNotRegisteredWhenLocalSwitchIsAbsent() {
        assertLocalSchedulingDisabled(null);
    }

    @Test
    void fallbackIsNotRegisteredWhenLocalSwitchIsFalse() {
        assertLocalSchedulingDisabled("false");
    }

    @Test
    void disabledFallbackDoesNotScanEvenIfOtherConfigurationEnablesScheduling() {
        try (var context = context("false", true)) {
            var processor = context.getBean(ScheduledAnnotationBeanPostProcessor.class);
            assertEquals(1, processor.getScheduledTasks().size());
            processor.getScheduledTasks().iterator().next().getTask().getRunnable().run();
            verifyNoInteractions(context.getBean(TradeOrderUpdateService.class), context.getBean(TenantFrameworkService.class));
        }
    }

    private void assertLocalSchedulingDisabled(String enabled) {
        try (var context = context(enabled)) {
            assertEquals("false", context.getEnvironment().getProperty("xxl.job.enabled"));
            assertTrue(context.getBeansOfType(TradeOrderSchedulingConfiguration.class).isEmpty());
            assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty());
            assertFalse(context.containsBean("xxlJobExecutor"));
            // 即使有人直接调用本地兜底入口，缺省/false 都不能触发真实业务。
            context.getBean(TradeOrderAutoCancelJob.class).fallbackExecute();
            verifyNoInteractions(context.getBean(TradeOrderUpdateService.class), context.getBean(TenantFrameworkService.class));
            verify(context.getBean(TaskScheduler.class), never()).scheduleWithFixedDelay(
                    any(Runnable.class), any(Instant.class), any(Duration.class));
        }
    }

    private AnnotationConfigApplicationContext context(String enabled) {
        return context(enabled, false);
    }

    private AnnotationConfigApplicationContext context(String enabled, boolean otherScheduling) {
        var context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("xxl.job.enabled", "false");
        if (enabled != null) properties.put("txgy.trade.order.local-auto-cancel-enabled", enabled);
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("isolated-scheduler-test", properties));
        context.register(TradeOrderSchedulingConfiguration.class, TxgyXxlJobAutoConfiguration.class, TestBeans.class);
        if (otherScheduling) context.register(OtherSchedulingConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class OtherSchedulingConfiguration {
        // 模拟其他共享配置启用调度，不启动真实 XXL-JOB。
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        TradeOrderAutoCancelJob tradeOrderAutoCancelJob() {
            return new TradeOrderAutoCancelJob();
        }

        @Bean
        TradeOrderUpdateService tradeOrderUpdateService() {
            return mock(TradeOrderUpdateService.class);
        }

        @Bean
        TenantFrameworkService tenantFrameworkService() {
            return mock(TenantFrameworkService.class);
        }

        @Bean
        TaskScheduler taskScheduler() {
            TaskScheduler scheduler = mock(TaskScheduler.class);
            when(scheduler.getClock()).thenReturn(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
            doReturn(mock(ScheduledFuture.class)).when(scheduler)
                    .scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));
            return scheduler;
        }
    }
}
