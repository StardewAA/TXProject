package cn.iocoder.txgy.framework.mybatis.core.interceptor;

import cn.iocoder.txgy.framework.common.util.audit.WorkTrace;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkTraceInterceptorTest {
    private DriverManagerDataSource fixture(String name) throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try(var c=ds.getConnection(); var s=c.createStatement()) {
            s.execute("CREATE TABLE wms_trace_stock(id BIGINT PRIMARY KEY, tenant_id BIGINT, quantity INT)");
            s.execute("INSERT INTO wms_trace_stock VALUES(1,11,3)");
        }
        return ds;
    }
    interface StockMapper {
        @Update("UPDATE wms_trace_stock SET quantity=#{quantity} WHERE id=#{id} AND tenant_id=#{tenant}")
        int update(@Param("id") long id, @Param("tenant") long tenant, @Param("quantity") int quantity);
    }
    @Test void batchFlushProducesBeforeAndAfter() throws Exception {
        var ds=fixture("traceBatch");
        var config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));
        config.addInterceptor(new WorkTraceInterceptor()); config.addMapper(StockMapper.class);
        WorkTrace.begin();
        try(var session=new SqlSessionFactoryBuilder().build(config).openSession(ExecutorType.BATCH)) {
            session.getMapper(StockMapper.class).update(1,11,8);
            session.flushStatements();
            String json=WorkTrace.snapshot();
            assertTrue(json.contains("\"quantity\":3"));
            assertTrue(json.contains("\"quantity\":8"));
            session.rollback();
        } finally { WorkTrace.clear(); }
    }
    @Test void springRollbackIsMarkedAndDoesNotPersistMutation() throws Exception {
        var ds=fixture("traceRollback");
        var config=new Configuration(new Environment("test",new org.mybatis.spring.transaction.SpringManagedTransactionFactory(),ds));
        config.addInterceptor(new WorkTraceInterceptor()); config.addMapper(StockMapper.class);
        var factory=new SqlSessionFactoryBuilder().build(config);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        WorkTrace.begin();
        try {
            tx.execute(status -> {
                try(var session=factory.openSession()) { session.getMapper(StockMapper.class).update(1,11,9); }
                status.setRollbackOnly(); return null;
            });
            assertTrue(WorkTrace.snapshot().contains("ROLLED_BACK"));
            try(var c=ds.getConnection();var s=c.createStatement();var rs=s.executeQuery("SELECT quantity FROM wms_trace_stock WHERE id=1")) {
                assertTrue(rs.next()); assertEquals(3,rs.getInt(1));
            }
        } finally { WorkTrace.clear(); }
    }
    @Test void recordsActualBeforeAndAfterWithoutCrossTenantRows() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:trace;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var c = ds.getConnection(); var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS wms_trace_stock");
            s.execute("CREATE TABLE wms_trace_stock(id BIGINT PRIMARY KEY, tenant_id BIGINT, quantity INT, access_token VARCHAR(30))");
            s.execute("INSERT INTO wms_trace_stock VALUES(1,11,3,'secret-a'),(2,22,7,'secret-b')");
        }
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.addInterceptor(new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor());
        config.addInterceptor(new WorkTraceInterceptor());
        config.addMapper(StockMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(config);
        WorkTrace.begin();
        try (var session = factory.openSession()) {
            assertEquals(1, session.getMapper(StockMapper.class).update(1,11,5));
            assertEquals(0, session.getMapper(StockMapper.class).update(2,11,10));
            String json = WorkTrace.snapshot();
            Map<?,?> snapshot = JsonUtils.parseObject(json, Map.class);
            List<?> entries = (List<?>) snapshot.get("entries");
            assertEquals(1, entries.size());
            Map<?,?> entry = (Map<?,?>) entries.getFirst();
            Map<?,?> before = (Map<?,?>) ((List<?>)entry.get("before")).getFirst();
            Map<?,?> after = (Map<?,?>) ((List<?>)entry.get("after")).getFirst();
            assertEquals(3, ((Number)before.get("quantity")).intValue());
            assertEquals(5, ((Number)after.get("quantity")).intValue());
            assertFalse(json.contains("secret-a"));
            assertFalse(json.contains("secret-b"));
            session.rollback();
        } finally { WorkTrace.clear(); }
        try(var c = ds.getConnection(); var s = c.createStatement(); var rs = s.executeQuery("SELECT quantity FROM wms_trace_stock WHERE id=1")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1)); // audit must not commit the business transaction
        }
    }
}
