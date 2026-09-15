package cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest;
// 规范依据：设计文档 v0.6 §五.1/§七/§十五.1（采集→主档 NEW，origUrlHash 唯一去重幂等；附件子表；PDF 红线8）

/**
 * 采集落库服务：外部网站公告原文/结构化为准后的主档录入（status=NEW 待审核）。
 */
public interface ExternalNoticeIngestService {

    /**
     * 落库一条外部公告（含附件子表）。
     *
     * @param dto 采集产出（HTML 原文 / PDF file_id / 附件列表 / 动态字段 metaJson）
     * @return 主档 ID（同 origUrlHash 已存在时返回既有 ID，不重复入库——幂等去重）
     */
    Long ingest(ExternalNoticeIngestDTO dto);

}
