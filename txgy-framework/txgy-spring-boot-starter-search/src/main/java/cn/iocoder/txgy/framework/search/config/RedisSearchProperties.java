package cn.iocoder.txgy.framework.search.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 独立检索 Redis 的配置。骨架阶段仅绑定配置，不建立连接或创建索引。
 * 不复用或覆盖 spring.data.redis 的缓存连接。
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "txgy.search.redis")
public class RedisSearchProperties {

    /** 预留开关；实际检索实现完成前保持关闭。 */
    private boolean enabled = false;

    @NotBlank
    private String host = "127.0.0.1";

    @Min(1)
    @Max(65535)
    private int port = 6380;

    private String username;
    private String password;
    private boolean ssl = false;

    @Min(1)
    private int timeoutMillis = 3000;

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9:_-]+")
    /** 公共根前缀；具体实现再追加 collection 和 tenantId。 */
    private String indexPrefix = "txgy:cms";

}
