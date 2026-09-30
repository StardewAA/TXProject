package cn.iocoder.txgy.module.bidding.infra.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * manual-advance-enabled（「下一步」代替时间轴流转）开关逻辑：
 * 缺省跟随模式（TEST=true / PROD=false），显式配置覆盖模式默认。
 */
class BiddingFlowPropertiesTest {

    @Test
    void default_shouldFollowMode() {
        BiddingFlowProperties props = new BiddingFlowProperties();
        // mode 缺省 TEST：允许手动下一步
        assertTrue(props.isManualAdvanceEnabled());
        // PROD：禁止（时间轴事件走定时任务）
        props.setMode("PROD");
        assertFalse(props.isManualAdvanceEnabled());
    }

    @Test
    void explicit_shouldOverrideMode() {
        // PROD 联调期显式 true：临时允许手动推进
        BiddingFlowProperties prod = new BiddingFlowProperties();
        prod.setMode("PROD");
        prod.setManualAdvanceEnabled(true);
        assertTrue(prod.isManualAdvanceEnabled());
        // TEST 显式 false：预演实战行为（时间轴事件不可手动）
        BiddingFlowProperties test = new BiddingFlowProperties();
        test.setManualAdvanceEnabled(false);
        assertFalse(test.isManualAdvanceEnabled());
    }

    @Test
    void invalidMode_shouldFallbackTest() {
        BiddingFlowProperties props = new BiddingFlowProperties();
        props.setMode("XXX");
        assertTrue(props.isTestMode());
        assertTrue(props.isManualAdvanceEnabled());
    }
}
