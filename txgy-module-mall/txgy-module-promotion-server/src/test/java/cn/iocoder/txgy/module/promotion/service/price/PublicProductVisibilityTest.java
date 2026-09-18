package cn.iocoder.txgy.module.promotion.service.price;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.stream.LongStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicProductVisibilityTest {
    private final ProductSpuApi api = mock(ProductSpuApi.class);
    private final PublicProductVisibility service = new PublicProductVisibility();
    private final Map<Long, Boolean> flags = new HashMap<>();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "productSpuApi", api);
        when(api.getSpuList(anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return CommonResult.success(ids.stream().filter(flags::containsKey)
                    .map(id -> new ProductSpuRespDTO().setId(id).setSaleable(flags.get(id))).toList());
        });
    }

    @Test void absentPendingAndUnknownProductsCannotAppearInPublicList() {
        flags.put(1L, true); flags.put(2L, false); flags.put(3L, null);
        var result = service.filter(List.of(1L, 2L, 3L, 4L), id -> id);
        assertEquals(List.of(1L), result.list());
        assertEquals(List.of(1L), result.spus().stream().map(ProductSpuRespDTO::getId).toList());
    }

    @Test void emptyListDoesNotInvokeUnboundedProductQuery() {
        assertTrue(service.filter(List.<Long>of(), id -> id).list().isEmpty());
        verifyNoInteractions(api);
    }

    @Test void staticCatalogueVisiblePageAndTotalAcrossHiddenSourcePages() {
        var all = LongStream.rangeClosed(1, 230).boxed().toList();
        all.forEach(id -> flags.put(id, id % 2 == 0));
        List<Integer> seen = new ArrayList<>();
        var result = service.page(new PageParam().setPageNo(6).setPageSize(20), param -> {
            seen.add(param.getPageNo());
            int start = (param.getPageNo() - 1) * param.getPageSize();
            return new PageResult<>(all.subList(start, Math.min(start + param.getPageSize(), all.size())), 230L);
        }, id -> id);
        assertEquals(115L, result.page().getTotal());
        assertEquals(LongStream.rangeClosed(201, 230).filter(n -> n % 2 == 0).boxed().toList(), result.page().getList());
        assertEquals(15, result.spus().size());
        assertEquals(List.of(1, 2, 3), seen);
    }

    @Test void completelyHiddenCatalogueReturnsTrueZero() {
        flags.put(1L, false);
        var result = service.page(new PageParam().setPageNo(1).setPageSize(10),
                param -> new PageResult<>(List.of(1L), 1L), id -> id);
        assertEquals(0L, result.page().getTotal());
        assertTrue(result.page().getList().isEmpty());
    }

    @Test void outOfRangePageKeepsTrueTotal() {
        flags.put(1L, true);
        var result = service.page(new PageParam().setPageNo(2).setPageSize(10),
                param -> new PageResult<>(List.of(1L), 1L), id -> id);
        assertEquals(1L, result.page().getTotal());
        assertTrue(result.page().getList().isEmpty());
    }

    @Test void recheckingAfterProductEditDoesNotUseCachedApproval() {
        flags.put(1L, true);
        assertEquals(List.of(1L), service.filter(List.of(1L), id -> id).list());
        flags.put(1L, false);
        assertTrue(service.filter(List.of(1L), id -> id).list().isEmpty());
        verify(api, times(2)).getSpuList(anyCollection());
    }

    @Test void catalogueCannotReuseAnotherTenantsPreviouslyVisibleDto() {
        flags.put(1L, true);
        service.filter(List.of(1L), id -> id);
        flags.clear();
        assertTrue(service.filter(List.of(1L), id -> id).list().isEmpty());
    }

    @Test void rpcFailureNeverFallsBackToPrivateData() {
        when(api.getSpuList(anyCollection())).thenReturn(CommonResult.error(500, "unavailable"));
        assertThrows(RuntimeException.class, () -> service.filter(List.of(1L), id -> id));
    }

    @Test void detailRequiresPositiveSaleableFlag() {
        when(api.getSpu(1L)).thenReturn(CommonResult.success(new ProductSpuRespDTO().setId(1L).setSaleable(false)));
        assertFalse(service.isSaleable(1L));
        when(api.getSpu(1L)).thenReturn(CommonResult.success(null));
        assertFalse(service.isSaleable(1L));
        assertFalse(service.isSaleable(null));
    }

    @Test void pageSizeAllCannotBypassPublicLimit() {
        assertEquals(400, assertThrows(ServiceException.class, () -> service.page(new PageParam().setPageSize(-1),
                param -> PageResult.<Long>empty(), id -> id)).getCode());
        verifyNoInteractions(api);
    }

    @Test void nullPageAndInvalidPageNumbersAreExplicitBadRequest() {
        List<PageParam> requests = Arrays.asList(null, new PageParam().setPageNo(null),
                new PageParam().setPageNo(0), new PageParam().setPageNo(-1));
        requests.forEach(request -> assertEquals(400, assertThrows(ServiceException.class,
                () -> service.page(request, param -> { fail("invalid request must not query"); return PageResult.<Long>empty(); }, id -> id)).getCode()));
        verifyNoInteractions(api);
    }

    @Test void nullZeroNegativeAndAbove200SizesAreExplicitBadRequest() {
        for (Integer size : Arrays.asList(null, 0, -1, 201)) {
            assertEquals(400, assertThrows(ServiceException.class, () -> service.page(new PageParam().setPageSize(size),
                    param -> { fail("invalid size must not query"); return PageResult.<Long>empty(); }, id -> id)).getCode());
            assertEquals(400, assertThrows(ServiceException.class, () -> service.first(size,
                    param -> { fail("invalid count must not query"); return PageResult.<Long>empty(); }, id -> id)).getCode());
        }
        verifyNoInteractions(api);
    }

    @Test void existing101And200PageSizesRemainValidWith100RowScanBatches() {
        List<Long> all = LongStream.rangeClosed(1, 230).boxed().toList();
        all.forEach(id -> flags.put(id, true));
        for (int size : List.of(101, 200)) {
            var result = service.page(new PageParam().setPageSize(size), param -> {
                assertEquals(100, param.getPageSize());
                return sourcePage(all, param);
            }, id -> id);
            assertEquals(size, result.page().getList().size());
            assertEquals(230L, result.page().getTotal());
        }
    }

    @Test void firstStopsWhenRequestedVisibleItemsFoundEvenForLargeCatalogue() {
        List<Long> firstBatch = LongStream.rangeClosed(1, 100).boxed().toList();
        firstBatch.forEach(id -> flags.put(id, true));
        AtomicInteger queries = new AtomicInteger();
        var result = service.first(6, param -> {
            assertEquals(1, queries.incrementAndGet(), "homepage must not scan the rest for total");
            return new PageResult<>(firstBatch, 50_000L);
        }, id -> id);
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), result.list());
        assertEquals(result.list(), result.spus().stream().map(ProductSpuRespDTO::getId).toList());
        verify(api, times(1)).getSpuList(anyCollection());
    }

    @Test void first200RemainsValidAndStopsAfterTwoBatches() {
        List<Long> all = LongStream.rangeClosed(1, 230).boxed().toList();
        all.forEach(id -> flags.put(id, true));
        AtomicInteger queries = new AtomicInteger();
        var result = service.first(200, param -> { queries.incrementAndGet(); return sourcePage(all, param); }, id -> id);
        assertEquals(200, result.list().size());
        assertEquals(2, queries.get());
    }

    @Test void firstSkipsHiddenBatchesAndDoesNotLeakUnusedDtos() {
        List<Long> all = LongStream.rangeClosed(1, 230).boxed().toList();
        flags.put(101L, true); flags.put(203L, true); flags.put(204L, true); flags.put(205L, true);
        var result = service.first(3, param -> sourcePage(all, param), id -> id);
        assertEquals(List.of(101L, 203L, 204L), result.list());
        assertEquals(result.list(), result.spus().stream().map(ProductSpuRespDTO::getId).toList());
    }

    @Test void exhaustedFirstCatalogueReturnsItsAvailableItemsWithoutInventingMore() {
        flags.put(1L, true);
        var result = service.first(6, param -> new PageResult<>(List.of(1L, 2L), 2L), id -> id);
        assertEquals(List.of(1L), result.list());
    }

    @Test void oversizedPagedCatalogueFails503BeforeReturningPartialPageOrDoingProductRpc() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.page(new PageParam(),
                param -> new PageResult<>(List.of(1L), 10_001L), id -> id));
        assertEquals(503, error.getCode());
        assertTrue(error.getMessage().contains("10000"));
        verifyNoInteractions(api);
    }

    @Test void exactly10000ActivitiesCanBeScannedAndCounted() {
        List<Long> all = LongStream.rangeClosed(1, 10_000).boxed().toList();
        flags.put(1L, true); flags.put(10_000L, true);
        AtomicInteger queries = new AtomicInteger();
        var result = service.page(new PageParam(), param -> { queries.incrementAndGet(); return sourcePage(all, param); }, id -> id);
        assertEquals(List.of(1L, 10_000L), result.page().getList());
        assertEquals(2L, result.page().getTotal());
        assertEquals(100, queries.get());
    }

    @Test void firstCannotScanBeyond10000OrReturnPartiallyFoundItems() {
        List<Long> all = LongStream.rangeClosed(1, 10_001).boxed().toList();
        flags.put(1L, true);
        AtomicInteger queries = new AtomicInteger();
        ServiceException error = assertThrows(ServiceException.class, () -> service.first(6,
                param -> { queries.incrementAndGet(); return sourcePage(all, param); }, id -> id));
        assertEquals(503, error.getCode());
        assertEquals(100, queries.get());
    }

    @Test void directOversizedListCannotBypassScanBudget() {
        assertEquals(503, assertThrows(ServiceException.class, () -> service.filter(
                LongStream.rangeClosed(1, 10_001).boxed().toList(), id -> id)).getCode());
        verifyNoInteractions(api);
    }

    @Test void timeoutAfterDatabaseQueryReturns503BeforeProductLookup() {
        AtomicLong clock = new AtomicLong();
        PublicProductVisibility timed = timedService(clock);
        ServiceException error = assertThrows(ServiceException.class, () -> timed.page(new PageParam(), param -> {
            clock.set(30_000_000_000L);
            return new PageResult<>(List.of(1L), 1L);
        }, id -> id));
        assertEquals(503, error.getCode());
        assertTrue(error.getMessage().contains("30秒"));
        verifyNoInteractions(api);
    }

    @Test void timeoutAfterProductRpcCannotReturnEvenACompleteHomepageList() {
        AtomicLong clock = new AtomicLong();
        PublicProductVisibility timed = timedService(clock);
        when(api.getSpuList(anyCollection())).thenAnswer(call -> {
            clock.set(30_000_000_000L);
            return CommonResult.success(List.of(new ProductSpuRespDTO().setId(1L).setSaleable(true)));
        });
        assertEquals(503, assertThrows(ServiceException.class, () -> timed.first(1,
                param -> new PageResult<>(List.of(1L), 1L), id -> id)).getCode());
    }

    @Test void withinTimeBudgetCanReturnNormally() {
        AtomicLong clock = new AtomicLong();
        PublicProductVisibility timed = timedService(clock);
        flags.put(1L, true);
        var result = timed.page(new PageParam(), param -> {
            clock.set(29_999_999_999L);
            return new PageResult<>(List.of(1L), 1L);
        }, id -> id);
        assertEquals(List.of(1L), result.page().getList());
    }

    private PublicProductVisibility timedService(AtomicLong clock) {
        PublicProductVisibility timed = new PublicProductVisibility(clock::get);
        ReflectionTestUtils.setField(timed, "productSpuApi", api);
        return timed;
    }

    private static PageResult<Long> sourcePage(List<Long> source, PageParam param) {
        int start = Math.min((param.getPageNo() - 1) * param.getPageSize(), source.size());
        return new PageResult<>(source.subList(start, Math.min(start + param.getPageSize(), source.size())), (long) source.size());
    }
}
