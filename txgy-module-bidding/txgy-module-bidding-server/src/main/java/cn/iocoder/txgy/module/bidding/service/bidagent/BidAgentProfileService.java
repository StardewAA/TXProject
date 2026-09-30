package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentProfileVO;

import java.util.List;

/** 标书智能体 - 智能体配置服务（后台调优入口 + 前台读取生效配置） */
public interface BidAgentProfileService {

    List<BidAgentProfileVO.RespVO> list();

    BidAgentProfileVO.RespVO get(Long id);

    /** 按任务类型取生效配置（前台编排调用；不存在返回 null，调用方回退内置提示词） */
    BidAgentProfileVO.RespVO getByTaskType(String taskType);

    Long save(BidAgentProfileVO.SaveReqVO reqVO);
}
