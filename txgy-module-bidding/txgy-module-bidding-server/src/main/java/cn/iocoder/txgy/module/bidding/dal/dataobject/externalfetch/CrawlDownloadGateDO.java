package cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch;

// 规范依据：《外部信息获取 v1.0》§六（消费端执行）/§11（频控）；用户口径 2026-09-10
//   「下载器的时间间隔必须且只能作用于『下载公告原文文件』这一动作」。
// 业务目标：记录"上一次发起原文下载"的时刻，作为合规限速的唯一真相（进程重启/多实例均有效）。

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 原文下载限速闸（G 轨，表 bidding_crawl_download_gate）。
 *
 * <p>为什么需要它：原实现的间隔是消费者进程内的 {@code volatile long lastConsumeAtMs}——
 * 重启即归零（重启后第一条消息立刻下载）、多实例各自计时（速率 ×N）、且与"是否真的下载"无关。
 * 改为本表后，占位由条件 UPDATE 原子完成（跨实例/跨重启一致），且只在下真实下载时消耗。
 */
@TenantIgnore // G 轨：表无 tenant_id 列，平台级数据（同 CrawlTaskDO 口径）
@TableName("bidding_crawl_download_gate")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlDownloadGateDO extends BaseDO {

    @TableId(type = IdType.INPUT) // 固定 id（GLOBAL=1）：幂等初始化，便于人工核对
    private Long id;
    /** 闸位作用域（GLOBAL=全局单闸） */
    private String scopeKey;
    /** 上次占位（=发起原文下载）时刻；null=从未下载 */
    private LocalDateTime lastDownloadAt;
    /** 上次占位对应任务 id（审计） */
    private Long lastTaskId;
    /** 上次下载地址（审计） */
    private String lastUrl;

}
