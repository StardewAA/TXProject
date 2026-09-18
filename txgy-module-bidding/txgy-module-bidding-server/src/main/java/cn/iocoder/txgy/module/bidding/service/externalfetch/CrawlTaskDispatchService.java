package cn.iocoder.txgy.module.bidding.service.externalfetch;

// 规范依据：外部信息获取 v1.0 §五（MySQL bidding_fetch_task=持久队列+唯一真相，Redis Stream=轻信号）；
//           生产端列表发现 -> 落 PENDING task + 发消息(taskId)；消费端 ACK 前不丢，崩溃由 pending 重投/启动自愈兜底。

import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.NoticeParseResult;

/**
 * 信息获取任务投递服务（生产端：列表发现的条目 -> 持久任务 + 轻信号消息）。
 *
 * <p>语义：
 * <ul>
 *   <li>唯一键/布隆防重：同 job+urlHash 已有 PENDING/RUNNING 直接复用（不重复发消息，队列级防重）；</li>
 *   <li>payloadJson 保存列表阶段快照（标题/URL/日期/栏目/全文引用），消费端回查执行；大文本不进 Redis；</li>
 *   <li>新建任务才发 CrawlNoticeFetchMessage（轻信号 taskId/jobId/sourceCode）。</li>
 * </ul>
 */
public interface CrawlTaskDispatchService {

    /**
     * 投递一条列表条目为信息获取任务：落 PENDING（复用防重）→ 写 payloadJson → 新任务发轻信号消息。
     *
     * @param jobId      批次 id（可为 null=无批次）
     * @param sourceCode 源 code
     * @param parserCode 解析/策略 code
     * @param entry      列表条目快照
     * @return taskId；null=命中复用未发消息（同任务已在队）
     */
    Long enqueue(Long jobId, String sourceCode, String parserCode, NoticeParseResult.ListEntry entry);

}