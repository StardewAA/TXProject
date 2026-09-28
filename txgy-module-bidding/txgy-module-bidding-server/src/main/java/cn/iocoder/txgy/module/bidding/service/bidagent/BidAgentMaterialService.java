package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentMaterialVO;

import java.util.Map;

/** 标书智能体 - 材料台账服务（后台） */
public interface BidAgentMaterialService {

    PageResult<BidAgentMaterialVO.RespVO> page(BidAgentMaterialVO.PageReqVO reqVO);

    BidAgentMaterialVO.RespVO get(Long id);

    /** 登记材料并触发入库；入库失败不回滚台账（保留失败原因供专员修正后重建） */
    Long register(BidAgentMaterialVO.SaveReqVO reqVO);

    void delete(Long id);

    /** 重建索引（切片被修改或入库失败后的修复入口） */
    void reindex(Long id);

    /** 片段分页（读本地切片台账，与向量库写入同源；契约端 /contract/segment/page 复用同一实现） */
    Map<String, Object> pageSegment(Long materialId, Integer pageNo, Integer pageSize);
}
