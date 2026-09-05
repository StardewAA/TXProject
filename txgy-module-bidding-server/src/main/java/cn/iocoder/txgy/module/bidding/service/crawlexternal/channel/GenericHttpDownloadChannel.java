package cn.iocoder.txgy.module.bidding.service.crawlexternal.channel;
// 规范依据：设计文档 v0.6 §四（通用下载器多通道占位；P1 落地后接 http-client + FileApi 落盘）

import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用 HTTP 下载通道（占位实现）。
 *
 * <p>待接入（注释即需求）：
 * <ol>
 *   <li>按 source 配置走 http-client（连接/读超时、UA、robots 开关）拉取 url；</li>
 *   <li>响应体判定形态：HTML 原文 / PDF（application/pdf）落 FileApi（pdf_file_id+sha256）或本地存储；</li>
 *   <li>增量：urlHash 最近一次成功抓取时间仅抓更新；非 2xx/超时按失败重试。</li>
 * </ol>
 * 当前为占位：调用直接以明确异常失败（计入重试计数，避免静默“假成功”），接入后替换实现即可。
 */
@Slf4j
@Component
public class GenericHttpDownloadChannel implements DownloadChannelAdapter {

    public static final String CHANNEL_CODE = "GENERIC";

    @Override
    public String getChannelCode() {
        return CHANNEL_CODE;
    }

    @Override
    public DownloadResult download(CrawlTaskDO task) {
        throw new IllegalStateException(
                "下载通道 GENERIC 为占位实现：请接入 http-client 下载 + FileApi/本地存储落盘（设计文档 v0.6 §四）后再启用采集，taskId="
                        + task.getId() + " url=" + task.getUrl());
    }

}
