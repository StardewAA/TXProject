package cn.iocoder.txgy.module.trade.job.portal;

import cn.iocoder.txgy.module.trade.service.portal.MallTenantSalesDailyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MallTenantSalesDailyRefreshJobTest {

    @Test
    void localFallbackIsDisabledByDefault() {
        MallTenantSalesDailyService service = mock(MallTenantSalesDailyService.class);
        MallTenantSalesDailyRefreshJob job = new MallTenantSalesDailyRefreshJob(service);

        job.fallbackExecute();

        verify(service, never()).refreshRecent(anyInt());
    }

    @Test
    void localFallbackRunsOnlyWhenExplicitlyEnabled() {
        MallTenantSalesDailyService service = mock(MallTenantSalesDailyService.class);
        MallTenantSalesDailyRefreshJob job = new MallTenantSalesDailyRefreshJob(service);
        ReflectionTestUtils.setField(job, "fallbackEnabled", true);

        job.fallbackExecute();
        String xxlResult = job.execute();

        verify(service).refreshRecent(MallTenantSalesDailyService.DEFAULT_WINDOW_DAYS);
        assertTrue(xxlResult.contains("本地调度"));
    }

    @Test
    void xxlEntryRemainsAvailableWhenFallbackIsDisabled() {
        MallTenantSalesDailyService service = mock(MallTenantSalesDailyService.class);
        when(service.refreshRecent(MallTenantSalesDailyService.DEFAULT_WINDOW_DAYS)).thenReturn(7);
        MallTenantSalesDailyRefreshJob job = new MallTenantSalesDailyRefreshJob(service);

        String result = job.execute();

        assertTrue(result.contains("生成 7 行"));
        verify(service).refreshRecent(MallTenantSalesDailyService.DEFAULT_WINDOW_DAYS);
    }
}
