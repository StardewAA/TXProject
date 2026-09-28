package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentPreviewVO;

/** 标书智能体 - 试跑（30 秒验证提示词，替代"跑一整册标书"） */
public interface BidAgentPreviewService {

    BidAgentPreviewVO.RespVO preview(BidAgentPreviewVO.ReqVO reqVO);
}
