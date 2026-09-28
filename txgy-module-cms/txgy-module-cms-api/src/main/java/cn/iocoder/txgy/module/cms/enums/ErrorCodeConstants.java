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
    ErrorCode PUBLICATION_URL_INVALID = new ErrorCode(1_041_000_007, "仅支持可公开访问的安全 HTTPS 原文链接");
    ErrorCode PUBLICATION_URL_FETCH_FAILED = new ErrorCode(1_041_000_008,
            "该政府网站拒绝自动访问，请改用具体法规的网页版原文链接，或上传文件、手动录入");
    ErrorCode PUBLICATION_URL_NOT_DOCUMENT = new ErrorCode(1_041_000_009,
            "请输入具体法规原文页链接，不能使用政府网站或法规数据库首页");
    ErrorCode KNOWLEDGE_CHUNK_NOT_FOUND = new ErrorCode(1_041_000_010, "知识库切片不存在或无权访问");
    ErrorCode KNOWLEDGE_CHUNK_VERSION_CONFLICT = new ErrorCode(1_041_000_011,
            "切片已被其他审查人员修改，请刷新后重试");
    ErrorCode KNOWLEDGE_CHUNK_SYNC_FAILED = new ErrorCode(1_041_000_012,
            "模型或检索服务异常，切片修改未生效");
    ErrorCode DOCUMENT_STORAGE_FAILED = new ErrorCode(1_041_000_013,
            "原文件持久化失败，本次提交未保存，请检查文件存储服务");
    ErrorCode FINANCE_POLICY_CATEGORY_REQUIRED = new ErrorCode(1_041_000_014,
            "该文档不属于企业金融政策库");
    ErrorCode FINANCE_POLICY_VERSION_CONFLICT = new ErrorCode(1_041_000_015,
            "金融政策信息已被其他人员修改，请刷新后重试");
    ErrorCode FINANCE_INTERPRETATION_UNAVAILABLE = new ErrorCode(1_041_000_016,
            "金融政策 AI 解读暂不可用，请检查场景模型配置");
    ErrorCode FINANCE_INTERPRETATION_INVALID = new ErrorCode(1_041_000_017,
            "AI 解读未通过结构或引用校验，请重试或人工处理");
    ErrorCode FINANCE_INTERPRETATION_NOT_FOUND = new ErrorCode(1_041_000_018,
            "尚未生成可审核的金融政策解读");
    ErrorCode CONSULT_EXPERT_NOT_FOUND = new ErrorCode(1_041_000_019, "专家不存在或尚未通过审核");
    ErrorCode CONSULT_ORDER_NOT_FOUND = new ErrorCode(1_041_000_020, "咨询订单不存在或无权访问");
    ErrorCode CONSULT_ORDER_STATUS_INVALID = new ErrorCode(1_041_000_021, "咨询订单当前状态不允许此操作");
    ErrorCode CONSULT_PAY_CALLBACK_INVALID = new ErrorCode(1_041_000_022, "支付回调与咨询订单不匹配");
    ErrorCode CONSULT_EVALUATION_DUPLICATE = new ErrorCode(1_041_000_023, "该咨询订单已经评价");
    ErrorCode CONSULT_SETTLEMENT_NOT_FOUND = new ErrorCode(1_041_000_024, "结算记录不存在或状态已变化");
    ErrorCode CONSULT_EXPERT_USER_INVALID = new ErrorCode(1_041_000_025, "只能聘请本企业已启用的用户账号作为专家");
    ErrorCode CONSULT_EXPERT_APPLY_REJECTED = new ErrorCode(1_041_000_026, "实名核验未通过，请核对姓名与身份证号");
    ErrorCode CONSULT_EXPERT_PHONE_DUPLICATED = new ErrorCode(1_041_000_027, "该手机号已被其他专家使用");
    ErrorCode KNOWLEDGE_PERMANENT_DELETE_NOT_ALLOWED = new ErrorCode(1_041_000_028,
            "只有已驳回或已归档的内容可以彻底删除");

    // ========== 五大库 CRUD（1_050_xxx_xxx 段） ==========
    ErrorCode STANDARD_NOT_EXISTS = new ErrorCode(1_050_001_000, "标准不存在");
    ErrorCode MATERIAL_NOT_EXISTS = new ErrorCode(1_050_002_000, "材料不存在");
    ErrorCode PROCESS_NOT_EXISTS = new ErrorCode(1_050_003_000, "工艺资源不存在");
    ErrorCode QUOTA_NOT_EXISTS = new ErrorCode(1_050_004_000, "定额资料不存在");
    ErrorCode POLICY_NOT_EXISTS = new ErrorCode(1_050_005_000, "政策资讯不存在");
    ErrorCode QUOTA_DETAIL_NOT_EXISTS = new ErrorCode(1_050_006_000, "定额明细不存在");
    ErrorCode CMS_CONTENT_NOT_EXISTS = new ErrorCode(1_050_007_000, "内容不存在");

}
