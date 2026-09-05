package cn.iocoder.txgy.module.bidding.service.notice.search;
// 规范依据：《招投标模块开发规范》§4.6（检索组件未启用禁止业务表 LIKE）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 未启用 ES 时的兜底引擎：索引/删除为空操作，关键词检索明确报错
 * （防止业务表 LIKE 全文查询，见规范 4.6/五章三第 3 款）。
 */
@Slf4j
@Component("devDisabledNoticeSearchEngine")
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DevDisabledSearchEngine implements NoticeSearchEngine {

    @Override
    public String name() {
        return "dev-disabled";
    }

    @Override
    public void index(NoticeEsDoc doc) {
        log.debug("[index][检索组件未启用，跳过索引 noticeId={}]", doc.getNoticeId());
    }

    @Override
    public void remove(Long noticeId) {
        log.debug("[remove][检索组件未启用，跳过删除 noticeId={}]", noticeId);
    }

    @Override
    public List<Long> matchIds(String keyword, int limit) {
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_SEARCH_UNAVAILABLE);
    }

    /** 占位空集合工具（保留语义：无命中） */
    public List<Long> empty() {
        return Collections.emptyList();
    }
}
