package cn.iocoder.txgy.module.bidding.config;
// [依据] 下载器定时调度开关（CrawlRunServiceImpl.scheduledScan 依赖）。

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class BiddingSchedulingConfig {

}
