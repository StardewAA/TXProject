package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentEvalVO;

/** 标书智能体 - 精准度测试用例（golden set）服务 */
public interface BidAgentEvalCaseService {

    PageResult<BidAgentEvalVO.CaseRespVO> page(BidAgentEvalVO.CasePageReqVO reqVO);

    Long save(BidAgentEvalVO.CaseSaveReqVO reqVO);

    void delete(Long id);
}
