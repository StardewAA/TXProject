package cn.iocoder.txgy.module.report.service.dataset;

import cn.iocoder.txgy.module.report.framework.dataset.config.ReportDatasourceProperties;
import com.alibaba.druid.pool.DruidDataSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.report.enums.ErrorCodeConstants.DATASET_EXECUTE_FAILED;
import static cn.iocoder.txgy.module.report.enums.ErrorCodeConstants.DATASET_RESULT_TOO_LARGE;

/**
 * 报表查询执行器
 *
 * 职责：使用**只读数据源**执行已参数化的 SQL，并在执行层强制：
 * 1. 查询超时（PreparedStatement#setQueryTimeout）；
 * 2. 行数上限（超过上限抛业务异常，而不是悄悄截断）；
 * 3. 结果集 -> 列名 + 明细的转换。
 *
 * @author OPENLAB BS
 */
@Slf4j
@Component
public class ReportQueryExecutor {

    private final JdbcTemplate jdbcTemplate;

    public ReportQueryExecutor(JdbcTemplate primaryJdbcTemplate, ReportDatasourceProperties properties) {
        if (properties.isConfigured()) {
            DruidDataSource dataSource = new DruidDataSource();
            dataSource.setUrl(properties.getUrl());
            dataSource.setUsername(properties.getUsername());
            dataSource.setPassword(properties.getPassword());
            dataSource.setInitialSize(properties.getInitialSize());
            dataSource.setMaxActive(properties.getMaxActive());
            dataSource.setValidationQuery("SELECT 1");
            dataSource.setTestWhileIdle(true);
            dataSource.setTestOnBorrow(false);
            dataSource.setTestOnReturn(false);
            this.jdbcTemplate = new JdbcTemplate(dataSource);
            log.info("[ReportQueryExecutor] 报表只读数据源已启用，最大连接数 {}", properties.getMaxActive());
        } else {
            this.jdbcTemplate = primaryJdbcTemplate;
            log.warn("[ReportQueryExecutor] 未配置 txgy.report.datasource.url，报表查询回退主库连接。" +
                    "生产环境必须配置只读账号，见 deploy/mysql/upgrade/20260916-report-dataset.sql");
        }
    }

    /**
     * 执行查询
     *
     * @param preparedSql   已参数化的 SQL（含租户与行数包装）
     * @param maxRows       行数上限（与 SQL 中的 LIMIT maxRows+1 对应）
     * @param timeoutSeconds 查询超时（秒）
     * @param source        来源标识，仅用于日志
     * @return 查询结果
     */
    public QueryResult query(ReportSqlGuard.PreparedSql preparedSql, int maxRows, int timeoutSeconds, String source) {
        try {
            ResultSetExtractor<QueryResult> extractor = resultSet -> buildResult(resultSet, maxRows);
            return jdbcTemplate.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(preparedSql.getSql());
                statement.setQueryTimeout(timeoutSeconds);
                List<Object> args = preparedSql.getArgs();
                for (int i = 0; i < args.size(); i++) {
                    statement.setObject(i + 1, args.get(i));
                }
                return statement;
            }, extractor);
        } catch (Exception ex) {
            // 业务异常（如超限）原样抛出，其余包装为查询失败
            if (ex instanceof cn.iocoder.txgy.framework.common.exception.ServiceException) {
                throw ex;
            }
            log.error("[query][{}] 报表查询失败 sql={}", source, preparedSql.getSql(), ex);
            throw exception(DATASET_EXECUTE_FAILED, ex.getMessage());
        }
    }

    private QueryResult buildResult(ResultSet resultSet, int maxRows) throws java.sql.SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<String> dimensions = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            dimensions.add(metaData.getColumnLabel(i));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            // SQL 已 LIMIT maxRows + 1：能读到第 maxRows + 1 行即视为超过上限
            if (rows.size() >= maxRows) {
                throw exception(DATASET_RESULT_TOO_LARGE, maxRows);
            }
            Map<String, Object> row = new LinkedHashMap<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.put(dimensions.get(i - 1), resultSet.getObject(i));
            }
            rows.add(row);
        }
        return new QueryResult(dimensions, rows);
    }

    /**
     * 查询结果
     */
    @Getter
    @AllArgsConstructor
    public static class QueryResult {

        private final List<String> dimensions;

        private final List<Map<String, Object>> rows;

    }

}
