package cn.iocoder.txgy.framework.search.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Elasticsearch 8.x 检索配置。
 * 使用自有命名空间，避免 Spring Boot 4.1 自动管理的 9.x 客户端混入。
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "txgy.search.elasticsearch")
public class ElasticsearchSearchProperties {

    /** 默认关闭，避免未部署 Elasticsearch 的服务在启动时误用搜索能力。 */
    private boolean enabled = false;

    @NotEmpty
    private List<@NotBlank String> uris = List.of("http://127.0.0.1:9200");

    private String username;
    private String password;

    @NotBlank
    @Pattern(regexp = "[a-z0-9_-]+")
    private String indexPrefix = "txgy-cms";

    @Valid
    private Duration connectTimeout = Duration.ofSeconds(3);

    @Valid
    private Duration socketTimeout = Duration.ofSeconds(10);

    @Min(1)
    private int bulkSize = 500;
}
