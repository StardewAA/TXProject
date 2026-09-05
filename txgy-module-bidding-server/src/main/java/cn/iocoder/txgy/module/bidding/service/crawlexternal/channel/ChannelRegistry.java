package cn.iocoder.txgy.module.bidding.service.crawlexternal.channel;
// 规范依据：设计文档 v0.6 §四（多通道配置化不写死；channelCode 路由；平台专属 Adapter 可插拔）

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 下载通道注册表：按 channel_code 路由。
 *
 * <p>通用实现（GENERIC）为默认占位；平台专属实现注册为 Bean 后由构造注入自动收集，
 * 源配置里写 channel_code 即路由（无匹配通道时报错，不静默走错通道）。
 */
@Component
public class ChannelRegistry {

    private final Map<String, DownloadChannelAdapter> channels = new HashMap<>();

    private final List<DownloadChannelAdapter> adapters;

    public ChannelRegistry(List<DownloadChannelAdapter> adapters) {
        this.adapters = adapters;
    }

    @PostConstruct
    public void init() {
        for (DownloadChannelAdapter adapter : adapters) {
            DownloadChannelAdapter old = channels.put(adapter.getChannelCode(), adapter);
            if (old != null) {
                throw new IllegalStateException("通道 code 重复注册: " + adapter.getChannelCode());
            }
        }
    }

    /** 取通道；未知 code 明确报错（防配置错乱静默抓错） */
    public DownloadChannelAdapter get(String channelCode) {
        DownloadChannelAdapter adapter = channels.get(channelCode);
        if (adapter == null) {
            throw new IllegalArgumentException("未知下载通道 channelCode=" + channelCode
                    + "，可用: " + channels.keySet());
        }
        return adapter;
    }

}
