package cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf;
// 规范依据：设计文档 v0.6 §7.3/§十五.6（PDF 本地抽取 SPI；多通道占位：OpenDataLoader 本地实现先行）

import java.io.File;

/**
 * PDF 内容抽取器 SPI（文本 + 表格）。
 * <p>当前唯一实现：本地 OpenDataLoader（设计文档 §15.6 决策：不依赖远端 OCR 服务，扫描件空结果直接判失败）。
 * 后续如需云端兜底通道，新增实现并在此 SPI 上按 channelCode 路由。
 */
public interface PdfContentExtractor {

    /**
     * 抽取 PDF 文本与表格。
     *
     * @param pdf 本地 PDF 文件（已下载落盘）
     * @return 抽取结果（可能为空结果，由调用方按空结果规则失败）
     */
    PdfExtractResult extract(File pdf);

}
