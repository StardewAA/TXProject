package cn.iocoder.txgy.module.cms.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * CMS 系统错误码，使用 1-041-000-000 段。
 */
public interface ErrorCodeConstants {

    ErrorCode LAW_SEARCH_UNAVAILABLE = new ErrorCode(1_041_000_000, "法规检索服务暂不可用，请检查 Elasticsearch 配置");
    ErrorCode LAW_ARTICLE_NOT_FOUND = new ErrorCode(1_041_000_001, "法规条款不存在或无权访问");
    ErrorCode LAW_RAG_UNAVAILABLE = new ErrorCode(1_041_000_002, "法规智能问答暂不可用，请检查模型与向量检索配置");
    ErrorCode LAW_RAG_NO_CONTEXT = new ErrorCode(1_041_000_003, "知识库中没有找到足够的法规原文依据");
    ErrorCode KNOWLEDGE_DOCUMENT_NOT_FOUND = new ErrorCode(1_041_000_004, "待发布文档不存在，只有审查通过的内容才能发布");

}
