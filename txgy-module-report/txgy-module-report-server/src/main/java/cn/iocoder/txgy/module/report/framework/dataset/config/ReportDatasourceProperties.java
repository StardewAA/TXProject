package cn.iocoder.txgy.module.report.framework.dataset.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 报表只读数据源配置
 *
 * 报表查询与业务写库分离：使用独立只读账号（仅 SELECT 权限），
 * 即使 SQL 被注入也无法写入数据，同时避免报表大查询占满业务连接池。
 *
 * url 留空时回退主库连接（会打 WARN，仅用于本地临时跑通，禁止上生产）。
 *
 * @author OPENLAB BS
 */
@ConfigurationProperties(prefix = "txgy.report.datasource")
@Component
@Data
public class ReportDatasourceProperties {

    /**
     * 只读数据源 JDBC URL
     */
    private String url;

    /**
     * 只读账号
     */
    private String username;

    /**
     * 只读账号密码
     */
    private String password;

    /**
     * 连接池初始连接数
     */
    private Integer initialSize = 1;

    /**
     * 连接池最大连接数
     */
    private Integer maxActive = 5;

    /**
     * 全局硬上限：任何数据集/原始 SQL 的 maxRows 都不得超过该值
     */
    private Integer maxRows = 5000;

    /**
     * 全局默认查询超时（秒）
     */
    private Integer timeoutSeconds = 30;

    /**
     * 是否已配置独立只读数据源
     */
    public boolean isConfigured() {
        return url != null && !url.isBlank();
    }

}
