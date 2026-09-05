package cn.iocoder.txgy.module.bidding.service.crawlexternal.channel;
// 规范依据：设计文档 v0.6 §四/§15.6（下载器多通道、配置化不写死：ChannelAdapter 接口 = 通用实现 + 平台专属实现，均在本仓库 Java 内）

import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;

/**
 * 下载通道适配器 SPI。
 *
 * <p>通道差异（平台 URL 编排/反爬频控/原文落地方式）收敛在实现上，由 source.channel_code 路由
 * （ChannelRegistry：GENERIC 通用 + 平台专属 code）。原文一律先落存储（FileApi/本地），供抽取/审核/展示回源。
 */
public interface DownloadChannelAdapter {

    /** 通道 code（与 bidding_crawl_source.channel_code 对应） */
    String getChannelCode();

    /**
     * 执行下载。
     *
     * @param task 已置 RUNNING 的采集任务
     * @return 下载结果（原文引用/内容形态）
     */
    DownloadResult download(CrawlTaskDO task);

}
