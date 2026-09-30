package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentModelVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentModelDO;
import cn.iocoder.txgy.module.bidding.framework.bidagent.LlmConfig;

import java.util.List;

/** 云端模型配置服务（智能体管理自持，不依赖平台 AI 模块） */
public interface BidAgentModelService {

    List<BidAgentModelVO.RespVO> list();

    BidAgentModelVO.RespVO get(Long id);

    Long save(BidAgentModelVO.SaveReqVO reqVO);

    /** 连通性自检（id 非空用库里 key，否则用入参 key） */
    BidAgentModelVO.TestRespVO test(BidAgentModelVO.TestReqVO reqVO);

    /** 取当前生效的模型配置（无启用配置时抛 1_060_111_093） */
    LlmConfig resolveEnabledConfig();

    /**
     * 取当前生效的模型 DO：租户自建启用配置优先，平台（tenant_id=0）默认配置兜底；
     * 无启用配置时返回 null（供执行侧运行时组装降级，不抛错）。
     */
    BidAgentModelDO resolveEnabledModelOrNull();
}
