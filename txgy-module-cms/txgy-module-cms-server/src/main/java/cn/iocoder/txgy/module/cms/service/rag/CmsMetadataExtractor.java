package cn.iocoder.txgy.module.cms.service.rag;


import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Metadata;


/** 从可信来源页面正文提取待用户确认的发布元数据。 */
public interface CmsMetadataExtractor {
    Extraction extract(String sourceUrl, String pageTitle, String pageText);
    record Extraction(Metadata metadata, String text) { }
}
