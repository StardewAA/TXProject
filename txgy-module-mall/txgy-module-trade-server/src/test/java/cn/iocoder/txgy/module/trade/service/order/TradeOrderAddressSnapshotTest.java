package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.txgy.module.trade.convert.order.TradeOrderConvert;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TradeOrderAddressSnapshotTest {
    MemberAddressRespDTO address() {
        return new MemberAddressRespDTO().setLongitude(new BigDecimal("116.397428")).setLatitude(new BigDecimal("39.909230"))
                .setCoordSystem("GCJ02").setLocationSource("MAP_PICKER").setLocationPrecision("BUILDING")
                .setLocationConfirmedAt(LocalDateTime.of(2026,9,17,9,0));
    }
    @Test void snapshotDoesNotFollowLaterAddressBookChanges() {
        var address=address();var order=new TradeOrderDO();TradeOrderAddressSnapshot.copy(address,order);
        address.setLongitude(BigDecimal.ONE).setLatitude(BigDecimal.TEN).setLocationConfirmedAt(LocalDateTime.now());
        assertEquals(new BigDecimal("116.397428"),order.getReceiverLongitude());
        assertEquals(LocalDateTime.of(2026,9,17,9,0),order.getReceiverLocationConfirmedAt());
    }
    @Test void oldAddressWithoutCoordinatesRemainsLegal() {
        var order=new TradeOrderDO();TradeOrderAddressSnapshot.copy(new MemberAddressRespDTO(),order);
        assertNull(order.getReceiverLongitude());assertNull(order.getReceiverLocationConfirmedAt());
    }
    @Test void buyerDetailRetainsCoordinateSnapshot() {
        var order=new TradeOrderDO();TradeOrderAddressSnapshot.copy(address(),order);
        var response=TradeOrderConvert.INSTANCE.convert3(order,java.util.List.of());
        assertEquals(order.getReceiverLongitude(),response.getReceiverLongitude());
        assertEquals(order.getReceiverLocationConfirmedAt(),response.getReceiverLocationConfirmedAt());
    }
    @Test void administratorAddressChangeClearsPointInSqlAndChecksOrderState() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:order-location-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE trade_order(id BIGINT PRIMARY KEY,status INT,receiver_name VARCHAR(100),"
                +"receiver_mobile VARCHAR(20),receiver_area_id INT,receiver_detail_address VARCHAR(250),receiver_longitude DECIMAL(10,6),"
                +"receiver_latitude DECIMAL(9,6),receiver_coord_system VARCHAR(16),receiver_location_source VARCHAR(32),"
                +"receiver_location_precision VARCHAR(32),receiver_location_confirmed_at TIMESTAMP,update_time TIMESTAMP,updater VARCHAR(64),deleted BOOLEAN DEFAULT FALSE)");
        jdbc.update("INSERT INTO trade_order(id,status,receiver_detail_address,receiver_longitude,receiver_latitude,receiver_coord_system,"
                +"receiver_location_source,receiver_location_precision,receiver_location_confirmed_at) VALUES(1,10,'旧地址',116.3,39.9,'GCJ02','MAP_PICKER','BUILDING',CURRENT_TIMESTAMP)");
        var c=new MybatisConfiguration();c.setMapUnderscoreToCamelCase(true);var g=new GlobalConfig();g.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
        var f=new MybatisSqlSessionFactoryBean();f.setDataSource(ds);f.setConfiguration(c);f.setGlobalConfig(g);
        var sf=f.getObject();sf.getConfiguration().addMapper(TradeOrderMapper.class);var mapper=new SqlSessionTemplate(sf).getMapper(TradeOrderMapper.class);
        var update=new TradeOrderDO().setId(1L).setReceiverName("测试收件人").setReceiverDetailAddress("新地址");
        assertEquals(0,mapper.updateAddressAndClearLocation(update,20));
        assertNotNull(jdbc.queryForObject("SELECT receiver_longitude FROM trade_order WHERE id=1",BigDecimal.class));
        assertEquals(1,mapper.updateAddressAndClearLocation(update,10));
        var row=jdbc.queryForMap("SELECT * FROM trade_order WHERE id=1");assertEquals("新地址",row.get("RECEIVER_DETAIL_ADDRESS"));
        assertNull(row.get("RECEIVER_LONGITUDE"));assertNull(row.get("RECEIVER_LATITUDE"));assertNull(row.get("RECEIVER_COORD_SYSTEM"));
        assertNull(row.get("RECEIVER_LOCATION_SOURCE"));assertNull(row.get("RECEIVER_LOCATION_PRECISION"));assertNull(row.get("RECEIVER_LOCATION_CONFIRMED_AT"));
    }
}
