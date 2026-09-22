package cn.iocoder.txgy.module.trade.dal.mysql.wms;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 不依赖租户插件执行真实 SQL，验证补偿候选查询自身具备完整租户边界。 */
class TradeWmsRecoveryMapperSqlTest {

    private SqlSession session;
    private TradeWmsRecoveryMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:wms_recovery_" + java.util.UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        var configuration = new Configuration(new Environment("wms-recovery",
                new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(TradeWmsRecoveryMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(TradeWmsRecoveryMapper.class);
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE trade_order(id BIGINT PRIMARY KEY, tenant_id BIGINT, no VARCHAR(30), "
                    + "pay_status BOOLEAN, status INT, deleted BOOLEAN)");
            statement.execute("CREATE TABLE trade_wms_shipment(id BIGINT PRIMARY KEY, tenant_id BIGINT, "
                    + "order_id BIGINT, deleted BOOLEAN)");
            statement.execute("INSERT INTO trade_order VALUES "
                    + "(1,1,'T1',TRUE,10,FALSE),"       // 唯一合法候选
                    + "(2,2,'T2',TRUE,10,FALSE),"       // 其它租户
                    + "(3,1,'T3',FALSE,0,FALSE),"       // 未支付
                    + "(4,1,'T4',TRUE,20,FALSE),"       // 已发货
                    + "(5,1,'T5',TRUE,10,FALSE),"       // 已有本租户出库记录
                    + "(6,1,'T6',TRUE,10,TRUE)");        // 已删除
            statement.execute("INSERT INTO trade_wms_shipment VALUES "
                    + "(51,1,5,FALSE),"                 // 只排除同租户订单
                    + "(52,2,1,FALSE)");                // 其它租户同 order_id 不得误伤
        }
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void selectsOnlyCurrentTenantPaidUndeliveredOrdersWithoutShipment() {
        var rows = mapper.selectPaidOrdersWithoutShipment(1L, 100);

        assertEquals(1, rows.size());
        assertEquals(1L, rows.getFirst().getId());
        assertEquals("T1", rows.getFirst().getNo());
    }

    @Test
    void enforcesTenantAndLimitInSql() {
        assertEquals(1, mapper.selectPaidOrdersWithoutShipment(1L, 1).size());
        assertEquals(1, mapper.selectPaidOrdersWithoutShipment(2L, 100).size());
        assertEquals(2L, mapper.selectPaidOrdersWithoutShipment(2L, 100).getFirst().getId());
        assertEquals(0, mapper.selectPaidOrdersWithoutShipment(3L, 100).size());
    }
}
