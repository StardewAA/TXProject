package cn.iocoder.txgy.module.bidding.service.externalfetch.bloom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 布隆实现切换契约测试：bidding.ext-notice.bloom.type=redis|local。
 * local 缺省装配（多节点未配置时单机可用）；redis 显式装配（多节点共享判定空间）。
 */
class BloomModeSwitchContractTest {

    @Test
    void local_defaultAndExplicit() {
        ConditionalOnProperty ann = LocalUrlBloomFilter.class.getAnnotation(ConditionalOnProperty.class);
        assertTrue(ann.matchIfMissing(), "local 应为默认实现（不配置时可用）");
        assertEquals("bidding.ext-notice.bloom", ann.prefix());
        assertEquals("type", ann.name()[0]);
        assertEquals("local", ann.havingValue());
    }

    @Test
    void redis_explicitOnly() {
        ConditionalOnProperty ann = RedisUrlBloomFilter.class.getAnnotation(ConditionalOnProperty.class);
        assertFalse(ann.matchIfMissing(), "redis 必须显式配置才装配（无 Redis 环境不炸）");
        assertEquals("redis", ann.havingValue());
    }

}
