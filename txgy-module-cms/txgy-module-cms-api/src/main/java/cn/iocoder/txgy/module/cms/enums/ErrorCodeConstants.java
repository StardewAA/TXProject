package cn.iocoder.txgy.module.cms.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * CMS 系统错误码，使用 1-041-000-000 段。
 */
public interface ErrorCodeConstants {

    ErrorCode LAW_SEARCH_UNAVAILABLE = new ErrorCode(1_041_000_000, "法规检索服务暂不可用，请检查 Elasticsearch 配置");
    ErrorCode LAW_ARTICLE_NOT_FOUND = new ErrorCode(1_041_000_001, "法规条款不存在或无权访问");

}
