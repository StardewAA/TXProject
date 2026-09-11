package cn.iocoder.txgy.module.mes.framework.sync.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MES 同步任务调度配置
 *
 * 开启 Spring @Scheduled 支持，供销售订单 → 项目同步任务的定时消费使用。
 *
 * @author OPENLAB BS
 */
@Configuration(value = "mesSyncScheduleConfiguration", proxyBeanMethods = false)
@EnableScheduling
public class MesSyncScheduleConfiguration {
}
