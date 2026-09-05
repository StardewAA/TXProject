package cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom;
// 规范依据：设计文档 v0.6 §二/§四（增量/去重判重）；安全/性能评审诉求：布隆过滤多节点化
// 说明：本门面是采集 URL 指纹判重的抽象。语义（正确性由 DB 唯一键兜底，本组件只做加速）：
//   1) mightContain=false ⇒ 一定未见过（布隆无假阴性）→ 调用方可免 DB 查询直插；
//   2) mightContain=true ⇒ “可能”见过（有假阳性）→ 仍需 DB 复核，不直接跳过（避免误丢公告）；
//   3) 插入撞唯一键（多节点竞态/重启窗口）→ 由 CrawlTaskService.createTaskIfAbsent 回退 DB 复用。
// 实现切换：bidding.ext-notice.bloom.type=local（Guava 单节点，默认）| redis（Redis 位图，多节点共享）。
public interface UrlBloomFilter {

    /** 可能见过（false=一定未见；true=需 DB 复核） */
    boolean mightContain(String urlHashHex);

    /** 标记已见（成功入库/复用后调用） */
    void mark(String urlHashHex);

}
