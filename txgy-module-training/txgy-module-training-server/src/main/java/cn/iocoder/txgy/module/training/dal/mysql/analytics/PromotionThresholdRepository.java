package cn.iocoder.txgy.module.training.dal.mysql.analytics;

import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PromotionThresholdRepository {
    @Resource private JdbcTemplate jdbc;

    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                StringBuilder key = new StringBuilder();
                boolean upper = false;
                for (char c : meta.getColumnLabel(i).toCharArray()) {
                    if (c == '_') upper = true;
                    else { key.append(upper ? Character.toUpperCase(c) : c); upper = false; }
                }
                Object value = rs.getObject(i);
                if (value instanceof Timestamp time) value = time.toLocalDateTime();
                if (value instanceof byte[] bytes && bytes.length == 1) value = bytes[0] != 0;
                row.put(key.toString(), value);
            }
            return row;
        }, args);
    }

    public Map<String, Object> one(String sql, Object... args) {
        var rows = list(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
}
