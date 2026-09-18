package cn.iocoder.txgy.module.crm.framework.schedule.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CRM 模块 Spring 定时任务配置
 *
 * <p>背景：框架的 {@code TxgyXxlJobAutoConfiguration} 会按
 * {@code xxl.job.enabled}（local/dev 默认 false）决定是否加载，其上的
 * {@code @EnableScheduling} 也随之失效，导致本模块基于 {@code @Scheduled}
 * 的内置定时任务（如售后回访任务逾期标记）不会执行。
 *
 * <p>此处无条件启用 Spring 定时任务，保证未接入 XXL-Job 调度中心时，
 * 逾期标记等能力仍开箱可用。
 *
 * @author txgycloud
 */
@Configuration
@EnableScheduling
public class CrmScheduleConfiguration {
}
