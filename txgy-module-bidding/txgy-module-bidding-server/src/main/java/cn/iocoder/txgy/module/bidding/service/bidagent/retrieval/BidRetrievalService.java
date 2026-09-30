package cn.iocoder.txgy.module.bidding.service.bidagent.retrieval;

import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidRetrieveReqDTO;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidRetrieveRespDTO;

/**
 * 投标检索服务：向量 + BM25 + 必检通道 + 阈值闸门 + 溯源标注（架构文档 §5）。
 */
public interface BidRetrievalService {

    /**
     * 检索证据片段（**契约端口径**：检索栈不可用时降级为 hit=false，让 Agent 转人工，不硬失败）
     *
     * @param reqDTO 检索请求
     * @return 检索结果；hit=false 时 Agent 必须中止生成转人工
     */
    BidRetrieveRespDTO retrieve(BidRetrieveReqDTO reqDTO);

    /**
     * 检索证据片段（**后台口径**：与契约端保持统一降级策略）
     *
     * <p>用在招投标侧「知识库管理 → 召回验证 / 精准度测试」：这两种场景是专员主动排障，
     * 云端不可用时优先回退 BM25；BM25 也不可用时才返回 hit=false。
     */
    BidRetrieveRespDTO retrieveStrict(BidRetrieveReqDTO reqDTO);
}
