package cn.iocoder.txgy.module.bidding.service.noticeexternal.search;
// 规范依据：设计文档 v0.6 §15.3/《招投标模块开发规范》§4.6（未启用检索组件时禁止业务表 LIKE 降级）

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检索组件未启用兜底引擎测试：关键词检索直接抛错（1-060-106-015），绝不静默降级为业务表 LIKE。
 */
class DevDisabledExternalNoticeSearchEngineTest {

    @Test
    void matchNoticeIds_throwsSearchUnavailable() {
        DevDisabledExternalNoticeSearchEngine engine = new DevDisabledExternalNoticeSearchEngine();
        assertEquals("dev", engine.name());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> engine.matchNoticeIds("关键词", 10));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_SEARCH_UNAVAILABLE.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("检索"));
    }
}
