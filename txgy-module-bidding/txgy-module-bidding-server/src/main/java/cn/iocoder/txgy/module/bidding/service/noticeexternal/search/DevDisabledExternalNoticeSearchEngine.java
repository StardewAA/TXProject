package cn.iocoder.txgy.module.bidding.service.noticeexternal.search;
// 规范依据：设计文档 v0.6 §15.3/《招投标模块开发规范》§4.6（未启用检索组件时禁止业务表 LIKE 降级）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 检索组件未启用时的兜底实现：关键词查询直接抛错提示（与站内公告策略一致），
 * 杜绝降级成业务表 LIKE（规范 4.6：不开放业务表模糊检索）。
 */
@Slf4j
@Component("devDisabledExternalNoticeSearchEngine")
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DevDisabledExternalNoticeSearchEngine implements ExternalNoticeSearchEngine {

    @Override
    public String name() {
        return "dev";
    }

    @Override
    public List<Long> matchNoticeIds(String keyword, int limit) {
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_SEARCH_UNAVAILABLE);
    }

}
