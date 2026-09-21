package cn.iocoder.txgy.module.promotion.service.price;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/**
 * 活动公开展示使用商品服务判定的可售版本，不改变历史订单和内部 RPC 的读取语义。
 * 不跨请求缓存，避免商品撤审/修改之后的旧可售标记，以及不同租户之间的缓存污染。
 */
@Component
public class PublicProductVisibility {
    private static final int BATCH_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_SCANNED_ACTIVITIES = 10_000;
    private static final long MAX_SCAN_NANOS = 30_000_000_000L;
    private final LongSupplier nanoClock;

    public PublicProductVisibility() { this(System::nanoTime); }

    /** Package-private clock injection keeps timeout tests deterministic without sleeping. */
    PublicProductVisibility(LongSupplier nanoClock) { this.nanoClock = Objects.requireNonNull(nanoClock); }

    @Resource
    private ProductSpuApi productSpuApi;

    public boolean isSaleable(Long spuId) {
        if (spuId == null) return false;
        ProductSpuRespDTO spu = productSpuApi.getSpu(spuId).getCheckedData();
        return spu != null && Boolean.TRUE.equals(spu.getSaleable());
    }

    public record VisibleList<T>(List<T> list, List<ProductSpuRespDTO> spus) {}
    public record VisiblePage<T>(PageResult<T> page, List<ProductSpuRespDTO> spus) {}

    public <T> VisibleList<T> filter(List<T> source, Function<T, Long> spuId) {
        ScanBudget budget = new ScanBudget();
        budget.record(source == null ? 0 : source.size());
        return filter(source, spuId, budget);
    }

    private <T> VisibleList<T> filter(List<T> source, Function<T, Long> spuId, ScanBudget budget) {
        budget.checkTime();
        if (source == null || source.isEmpty()) return new VisibleList<>(List.of(), List.of());
        List<Long> ids = source.stream().map(spuId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ProductSpuRespDTO> visible = new LinkedHashMap<>();
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            budget.checkTime();
            List<ProductSpuRespDTO> spus = productSpuApi.getSpuList(ids.subList(start,
                    Math.min(start + BATCH_SIZE, ids.size()))).getCheckedData();
            budget.checkTime();
            if (spus == null) throw exception0(503, "商品可售状态暂不可用，请稍后重试");
            for (ProductSpuRespDTO spu : spus) {
                if (spu != null && Boolean.TRUE.equals(spu.getSaleable())) visible.put(spu.getId(), spu);
            }
        }
        VisibleList<T> result = new VisibleList<>(source.stream().filter(row -> visible.containsKey(spuId.apply(row))).toList(),
                List.copyOf(visible.values()));
        budget.checkTime();
        return result;
    }

    /**
     * 跨服务无法直接在活动 SQL 中过滤商品审核状态；按小批次筛选后计算读到的页和总数，
     * 不把当前页过滤后的数量冒充总数。只保留目标页，内存不随活动总量增长。
     * 这是跨服务逐批读取，不是数据库一致性快照；并发目录变更可能影响本次分页结果。
     * 最多扫描10000条/30秒，超过限制明确失败，不返回不完整的总数或部分列表。
     * 活动目录大规模增长时应替换为可靠事件驱动的可售投影，而非复用跨租户缓存。
     */
    public <T> VisiblePage<T> page(PageParam requested, Function<PageParam, PageResult<T>> query,
                                  Function<T, Long> spuId) {
        if (requested == null || requested.getPageNo() == null || requested.getPageNo() < 1) {
            throw invalidParamException("页码必须为大于等于1的整数");
        }
        int size = validateCount(requested.getPageSize());
        long offset = (long) (requested.getPageNo() - 1) * size;
        long total = 0;
        List<T> rows = new ArrayList<>();
        Map<Long, ProductSpuRespDTO> pageSpus = new LinkedHashMap<>();
        ScanBudget budget = new ScanBudget();
        for (int pageNo = 1; ; pageNo++) {
            budget.beforeQuery();
            PageResult<T> batch = query.apply(new PageParam().setPageNo(pageNo).setPageSize(BATCH_SIZE));
            budget.record(batch.getList().size());
            // An exact full-directory count would require more work than this request permits.
            if (batch.getTotal() > MAX_SCANNED_ACTIVITIES) throw scanLimit();
            if (batch.getList().isEmpty()) break;
            VisibleList<T> visible = filter(batch.getList(), spuId, budget);
            Map<Long, ProductSpuRespDTO> mapped = new HashMap<>();
            visible.spus().forEach(spu -> mapped.put(spu.getId(), spu));
            for (T row : visible.list()) {
                if (total >= offset && rows.size() < size) {
                    rows.add(row);
                    pageSpus.put(spuId.apply(row), mapped.get(spuId.apply(row)));
                }
                total++;
            }
            if ((long) pageNo * BATCH_SIZE >= batch.getTotal()) break;
        }
        budget.checkTime();
        return new VisiblePage<>(new PageResult<>(rows, total), List.copyOf(pageSpus.values()));
    }

    /** 首页只需要前N个可售活动：凑齐即停，不扫描完整目录，也不声称返回总数。 */
    public <T> VisibleList<T> first(Integer count, Function<PageParam, PageResult<T>> query,
                                    Function<T, Long> spuId) {
        int size = validateCount(count);
        ScanBudget budget = new ScanBudget();
        List<T> rows = new ArrayList<>();
        Map<Long, ProductSpuRespDTO> pageSpus = new LinkedHashMap<>();
        for (int pageNo = 1; ; pageNo++) {
            budget.beforeQuery();
            PageResult<T> batch = query.apply(new PageParam().setPageNo(pageNo).setPageSize(BATCH_SIZE));
            budget.record(batch.getList().size());
            if (batch.getList().isEmpty()) break;
            VisibleList<T> visible = filter(batch.getList(), spuId, budget);
            Map<Long, ProductSpuRespDTO> mapped = new HashMap<>();
            visible.spus().forEach(spu -> mapped.put(spu.getId(), spu));
            for (T row : visible.list()) {
                rows.add(row);
                pageSpus.put(spuId.apply(row), mapped.get(spuId.apply(row)));
                if (rows.size() == size) break;
            }
            if (rows.size() == size || (long) pageNo * BATCH_SIZE >= batch.getTotal()) break;
        }
        budget.checkTime();
        return new VisibleList<>(rows, List.copyOf(pageSpus.values()));
    }

    private int validateCount(Integer count) {
        if (count == null || count < 1 || count > MAX_PAGE_SIZE) {
            throw invalidParamException("每页条数/展示数量必须为1至200的整数");
        }
        return count;
    }

    private cn.iocoder.txgy.framework.common.exception.ServiceException scanLimit() {
        return exception0(503, "活动目录超过单次扫描上限10000条，请缩小查询范围或稍后重试");
    }

    private final class ScanBudget {
        private final long started = nanoClock.getAsLong();
        private int scanned;

        void checkTime() {
            if (nanoClock.getAsLong() - started >= MAX_SCAN_NANOS) {
                throw exception0(503, "活动查询超过30秒，请稍后重试；本次未返回部分结果");
            }
        }

        void beforeQuery() {
            checkTime();
            if (scanned >= MAX_SCANNED_ACTIVITIES) throw scanLimit();
        }

        void record(int count) {
            checkTime();
            if (count > MAX_SCANNED_ACTIVITIES - scanned) throw scanLimit();
            scanned += count;
        }
    }
}
