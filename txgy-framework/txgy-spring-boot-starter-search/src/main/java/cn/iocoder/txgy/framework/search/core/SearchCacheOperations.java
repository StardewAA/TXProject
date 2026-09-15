package cn.iocoder.txgy.framework.search.core;

import java.time.Duration;
import java.util.Optional;

/** Lettuce 查询结果缓存契约；键由租户、索引版本和规范化查询摘要组成。 */
public interface SearchCacheOperations {
    Optional<String> get(String key);
    void put(String key, String value, Duration ttl);
    void evict(String key);
}
