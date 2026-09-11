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
    ErrorCode REVIEW_DUPLICATE = new ErrorCode(1_041_000_005, "内容与已有提交重复，请勿重复发布");
    ErrorCode REVIEW_NOT_READY = new ErrorCode(1_041_000_006, "AI 初审尚未完成，请稍后再进行人工复核");
    ErrorCode PUBLICATION_URL_INVALID = new ErrorCode(1_041_000_007, "仅支持安全的 HTTPS 政府网站原文链接");
    ErrorCode PUBLICATION_URL_FETCH_FAILED = new ErrorCode(1_041_000_008,
            "该政府网站拒绝自动访问，请改用具体法规的网页版原文链接，或上传文件、手动录入");
    ErrorCode PUBLICATION_URL_NOT_DOCUMENT = new ErrorCode(1_041_000_009,
            "请输入具体法规原文页链接，不能使用政府网站或法规数据库首页");

}
