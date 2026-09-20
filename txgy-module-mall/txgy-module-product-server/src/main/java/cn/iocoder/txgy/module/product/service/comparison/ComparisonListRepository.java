package cn.iocoder.txgy.module.product.service.comparison;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Arrays;
import java.util.List;

/** Explicit tenant/member predicates are required even where a tenant interceptor is unavailable. */
@Repository
public class ComparisonListRepository {
    @Resource private JdbcTemplate jdbcTemplate;
    public record Selection(long version, List<Long> skuIds, int quantity, String preference) {}

    public Selection get(long tenant, long member) {
        Selection result = jdbcTemplate.query("SELECT version,sku_ids,quantity,preference FROM product_comparison_list "
                        + "WHERE tenant_id=? AND member_id=?", (rs, row) -> new Selection(rs.getLong("version"),
                        decode(rs.getString("sku_ids")), rs.getInt("quantity"), rs.getString("preference")), tenant, member)
                .stream().findFirst().orElse(new Selection(0L, List.of(), 1, "PRICE"));
        if (result.version() < 0 || result.version() > 9_007_199_254_740_991L || result.quantity() < 1 || result.quantity() > 999
                || !java.util.Set.of("PRICE", "DELIVERY", "BALANCED").contains(result.preference()))
            throw new ServiceException(503, "对比清单暂时无法读取，请稍后重试");
        return result;
    }

    public Selection save(long tenant, long member, Selection value) {
        String ids = value.skuIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        long next = Math.addExact(value.version(), 1L);
        if (value.version() == 0) {
            try {
                jdbcTemplate.update("INSERT INTO product_comparison_list "
                                + "(tenant_id,member_id,version,sku_ids,quantity,preference) VALUES (?,?,?,?,?,?)",
                        tenant, member, next, ids, value.quantity(), value.preference());
            } catch (DuplicateKeyException conflict) { throw conflict(); }
        } else if (jdbcTemplate.update("UPDATE product_comparison_list SET version=?,sku_ids=?,quantity=?,preference=?,"
                        + "update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND member_id=? AND version=?",
                next, ids, value.quantity(), value.preference(), tenant, member, value.version()) != 1) {
            throw conflict();
        }
        return new Selection(next, List.copyOf(value.skuIds()), value.quantity(), value.preference());
    }

    private static ServiceException conflict() { return new ServiceException(409, "对比清单已在其他页面更新，请刷新后确认再保存"); }

    private static List<Long> decode(String ids) {
        if (ids == null || ids.isBlank()) return List.of();
        // Fail closed on corrupt rows rather than silently replacing a user's saved selection.
        try {
            List<Long> result = Arrays.stream(ids.split(",", -1)).map(Long::valueOf).toList();
            if (result.size() > 4 || result.stream().anyMatch(id -> id <= 0) || result.stream().distinct().count() != result.size())
                throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException badData) { throw new ServiceException(503, "对比清单暂时无法读取，请稍后重试"); }
    }
}
