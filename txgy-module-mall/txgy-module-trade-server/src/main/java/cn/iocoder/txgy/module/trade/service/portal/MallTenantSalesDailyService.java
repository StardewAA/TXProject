package cn.iocoder.txgy.module.trade.service.portal;

import cn.iocoder.txgy.module.trade.dal.mysql.portal.MallTenantSalesDailyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** Maintains the bounded public sales projection used by the portal ranking. */
@Service
@RequiredArgsConstructor
public class MallTenantSalesDailyService {

    public static final int DEFAULT_WINDOW_DAYS = 30;
    public static final int MAX_WINDOW_DAYS = 366;

    private final MallTenantSalesDailyMapper mapper;
    private final Clock clock = Clock.systemDefaultZone();

    /**
     * Replaces the requested date range atomically, so a retry cannot accumulate duplicate sales.
     */
    @Transactional(rollbackFor = Exception.class)
    public int refreshRecent(int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, MAX_WINDOW_DAYS));
        LocalDate toDate = LocalDate.now(clock);
        LocalDate fromDate = toDate.minusDays(days - 1L);
        mapper.deleteRange(fromDate, toDate);
        return mapper.insertRange(fromDate, toDate);
    }
}
