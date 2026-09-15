package cn.iocoder.txgy.module.wms.service.integration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
@TestConfiguration
public class WmsShortageTestConfiguration {
    @Bean JdbcTemplate shortageJdbc(DataSource source) { return new JdbcTemplate(source); }
}
