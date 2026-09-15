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
 * 独立向量检索 Redis 的配置。
 * 不复用或覆盖 spring.data.redis 的缓存连接。
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "txgy.search.redis")
public class RedisSearchProperties {

    /** 默认关闭，避免未部署 Redis 8 / Redis Stack 的服务误启用向量搜索。 */
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

    /** 必须与实际文本向量模型的输出维度完全一致。 */
    @Min(1)
    private int vectorDimensions = 768;

    /** Redis HNSW 的候选结果上限。 */
    @Min(1)
    @Max(1000)
    private int defaultTopK = 50;

    @NotBlank
    @Pattern(regexp = "COSINE|L2|IP")
    private String distanceMetric = "COSINE";

}
