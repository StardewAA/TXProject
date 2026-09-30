package cn.iocoder.txgy.module.trade.api.order;

import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderServiceCardMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real mapper SQL without a tenant plugin: its explicit predicates must be sufficient. */
class TradeOrderServiceCardSqlTest {
    SqlSession session;
    TradeOrderServiceCardMapper mapper;
    @BeforeEach void setup() throws Exception {
        var dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:service_card_" + java.util.UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        var configuration = new Configuration(new Environment("card", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(TradeOrderServiceCardMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(TradeOrderServiceCardMapper.class);
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE trade_order(id BIGINT PRIMARY KEY,tenant_id BIGINT,user_id BIGINT,shop_id BIGINT,"
                    + "no VARCHAR(30),pay_price INT,status INT,deleted INT,receiver_mobile VARCHAR(30))");
            statement.execute("INSERT INTO trade_order VALUES (8,1,11,101,'T8',1200,20,0,'private'),"
                    + "(9,2,11,202,'T9',2400,20,0,'private'),(10,1,22,101,'T10',500,10,0,'private'),"
                    + "(12,1,11,101,'T12',100,10,1,'private')");
        }
    }
    @AfterEach void close() { if (session != null) session.close(); }
    @Test void selectsOnlyOwnedVisibleOrderAndMapsSafeFields() {
        var result = mapper.selectOwned(8L,11L,1L);
        assertNotNull(result); assertEquals(1L,result.getTenantId()); assertEquals(11L,result.getUserId());
        assertEquals(101L,result.getShopId()); assertEquals(1200,result.getPayPrice()); assertEquals("T8",result.getNo());
        assertEquals(7, java.util.Arrays.stream(result.getClass().getDeclaredFields()).filter(f -> !f.isSynthetic()).count());
    }
    @Test void tenantOwnerDeletionAndMissingCannotLeak() {
        assertNull(mapper.selectOwned(9L,11L,1L)); assertNull(mapper.selectOwned(10L,11L,1L));
        assertNull(mapper.selectOwned(12L,11L,1L)); assertNull(mapper.selectOwned(999L,11L,1L));
    }
}
