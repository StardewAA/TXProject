package cn.iocoder.txgy.module.bidding.service.bidagent.shadow;

import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidTaskShadowReqDTO;

/**
 * Agent 任务影子状态服务
 */
public interface BidTaskShadowService {

    /**
     * 上报任务影子状态（幂等：按 taskId 覆盖）
     */
    void report(BidTaskShadowReqDTO reqDTO);
}
