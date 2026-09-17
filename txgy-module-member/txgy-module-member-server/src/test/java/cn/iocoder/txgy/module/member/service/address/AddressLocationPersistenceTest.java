package cn.iocoder.txgy.module.member.service.address;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.member.controller.app.address.vo.AppAddressCreateReqVO;
import cn.iocoder.txgy.module.member.controller.app.address.vo.AppAddressUpdateReqVO;
import cn.iocoder.txgy.module.member.convert.address.AddressConvert;
import cn.iocoder.txgy.module.member.dal.mysql.address.MemberAddressMapper;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Actual service / MyBatis / transaction / isolated H2. No real accounts or database touched. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AddressLocationPersistenceTest {
    AnnotationConfigApplicationContext context; AddressService service; JdbcTemplate jdbc;
    @BeforeAll void start() {
        context=new AnnotationConfigApplicationContext(Config.class);service=context.getBean(AddressService.class);
        jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("CREATE TABLE member_address(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,name VARCHAR(100),mobile VARCHAR(20),"
                +"area_id BIGINT,detail_address VARCHAR(250),default_status BOOLEAN,longitude DECIMAL(10,6),latitude DECIMAL(9,6),"
                +"coord_system VARCHAR(16),location_source VARCHAR(32),location_precision VARCHAR(32),location_confirmed_at TIMESTAMP,"
                +"creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE)");
    }
    @AfterAll void close(){context.close();}
    @BeforeEach void reset(){jdbc.update("DELETE FROM member_address");}
    AppAddressCreateReqVO create(boolean point) {
        var v=new AppAddressCreateReqVO();v.setName("隔离测试").setMobile("13900009999").setAreaId(110101L).setDetailAddress("隔离测试入口").setDefaultStatus(true);
        if(point)v.setLongitude(new BigDecimal("116.397428")).setLatitude(new BigDecimal("39.909230")).setCoordSystem("GCJ02")
                .setLocationSource("MAP_PICKER").setLocationPrecision("BUILDING").setLocationConfirmed(true);
        return v;
    }
    AppAddressUpdateReqVO update(Long id) {
        var v=new AppAddressUpdateReqVO();v.setId(id);v.setName("隔离测试").setMobile("13900009999").setAreaId(110101L).setDetailAddress("隔离测试入口").setDefaultStatus(true);return v;
    }
    @Test void locationPersistsAndRpcAndAppDtosExposeSameSnapshot(){
        long id=service.createAddress(71L,create(true));var old=service.getAddress(71L,id);
        assertEquals(new BigDecimal("116.397428"),old.getLongitude());assertNotNull(old.getLocationConfirmedAt());
        assertEquals(old.getLongitude(),AddressConvert.INSTANCE.convert02(old).getLongitude());
        assertEquals(old.getLocationConfirmedAt(),AddressConvert.INSTANCE.convert(old).getLocationConfirmedAt());
    }
    @Test void changingTextActuallyWritesSqlNull(){
        long id=service.createAddress(71L,create(true));var req=update(id);req.setDetailAddress("另一入口");service.updateAddress(71L,req);
        var stored=service.getAddress(71L,id);assertEquals("另一入口",stored.getDetailAddress());
        assertNull(stored.getLongitude());assertNull(stored.getLatitude());assertNull(stored.getCoordSystem());assertNull(stored.getLocationConfirmedAt());
    }
    @Test void explicitClearActuallyWritesSqlNull(){
        long id=service.createAddress(71L,create(true));var req=update(id);req.setClearLocation(true);service.updateAddress(71L,req);
        assertNull(service.getAddress(71L,id).getLongitude());
    }
    @Test void reconfirmedTextAndPointUpdateTogetherInDatabase(){
        long id=service.createAddress(71L,create(true));var req=update(id);
        req.setDetailAddress("重新确认的二号入口").setLongitude(new BigDecimal("116.500000")).setLatitude(new BigDecimal("39.800000"))
                .setCoordSystem("GCJ02").setLocationSource("GEOCODER").setLocationPrecision("APPROXIMATE").setLocationConfirmed(true);
        service.updateAddress(71L,req);var after=service.getAddress(71L,id);
        assertEquals("重新确认的二号入口",after.getDetailAddress());assertEquals(new BigDecimal("116.500000"),after.getLongitude());
        assertEquals("APPROXIMATE",after.getLocationPrecision());assertNotNull(after.getLocationConfirmedAt());
    }
    @Test void defaultAddressReassignmentDoesNotEraseAnotherAddressPoint(){
        long first=service.createAddress(71L,create(true));long second=service.createAddress(71L,create(false));
        assertFalse(service.getAddress(71L,first).getDefaultStatus());assertNotNull(service.getAddress(71L,first).getLongitude());
        assertEquals(second,service.getDefaultUserAddress(71L).getId());
    }
    @Test void legacyRecipientEditRetainsConfirmedPoint(){
        long id=service.createAddress(71L,create(true));var before=service.getAddress(71L,id);var req=update(id);req.setName("新收件人");service.updateAddress(71L,req);
        var after=service.getAddress(71L,id);assertEquals(before.getLongitude(),after.getLongitude());assertEquals(before.getLocationConfirmedAt(),after.getLocationConfirmedAt());
    }
    @Test void foreignMemberCannotReadChangeOrDeletePoint(){
        long id=service.createAddress(71L,create(true));assertNull(service.getAddress(72L,id));
        assertThrows(ServiceException.class,()->service.updateAddress(72L,update(id)));
        assertThrows(ServiceException.class,()->service.deleteAddress(72L,id));assertNotNull(service.getAddress(71L,id).getLongitude());
    }
    @Test void invalidPointRollsBackWithoutChangingDefault(){
        long id=service.createAddress(71L,create(true));var bad=create(true);bad.setLatitude(null);
        assertThrows(ServiceException.class,()->service.createAddress(71L,bad));assertEquals(id,service.getDefaultUserAddress(71L).getId());assertEquals(1,service.getAddressList(71L).size());
    }
    @Test void unchangedAddressBookPointNotSharedAcrossMembers(){
        long first=service.createAddress(71L,create(true));long second=service.createAddress(72L,create(false));
        assertNotNull(service.getDefaultUserAddress(71L).getLongitude());assertNull(service.getDefaultUserAddress(72L).getLongitude());
        assertEquals(first,service.getAddressList(71L).getFirst().getId());assertEquals(second,service.getAddressList(72L).getFirst().getId());
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    static class Config {
        @Bean DataSource dataSource(){var d=new JdbcDataSource();d.setURL("jdbc:h2:mem:address-location-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");return d;}
        @Bean PlatformTransactionManager tx(DataSource d){return new DataSourceTransactionManager(d);}
        @Bean SqlSessionFactory factory(DataSource d)throws Exception{
            var c=new MybatisConfiguration();c.setMapUnderscoreToCamelCase(true);var g=new GlobalConfig();g.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
            var f=new MybatisSqlSessionFactoryBean();f.setDataSource(d);f.setConfiguration(c);f.setGlobalConfig(g);var sf=f.getObject();sf.getConfiguration().addMapper(MemberAddressMapper.class);return sf;
        }
        @Bean MemberAddressMapper mapper(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(MemberAddressMapper.class);}
        @Bean AddressService service(){return new AddressServiceImpl();}
    }
}
