package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentEvalVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentRetrieveVO;

/** 标书智能体 - 召回验证与精准度测试（后台调优 + 前台共用检索入口） */
public interface BidAgentRetrieveService {

    /** 检索（召回验证页与前台编排共用；hit=false 时调用方必须转人工） */
    BidAgentRetrieveVO.RespVO retrieve(BidAgentRetrieveVO.ReqVO reqVO);

    /** 跑精准度测试，输出 Recall@K 与未命中清单 */
    BidAgentEvalVO.RunRespVO runEval(BidAgentEvalVO.RunReqVO reqVO);
}
