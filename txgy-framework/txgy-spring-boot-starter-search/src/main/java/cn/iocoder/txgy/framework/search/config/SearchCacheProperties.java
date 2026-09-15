package cn.iocoder.txgy.framework.search.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Lettuce 查询结果缓存配置。缓存不是资料事实来源，也不承担全文检索。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "txgy.search.cache")
public class SearchCacheProperties {

    private boolean enabled = false;

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9:_-]+")
    private String keyPrefix = "txgy:cms:search";

    private Duration ttl = Duration.ofMinutes(5);
}
