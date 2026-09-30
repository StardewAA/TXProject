package cn.iocoder.txgy.module.bidding.service.bidagent.fact;

import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidFactRespDTO;

import java.util.List;

/**
 * 企业事实服务（COPY_FACT 通道的唯一数据源）
 */
public interface BidFactService {

    /**
     * 查询企业事实
     *
     * @param tenantId 租户编号
     * @param types    类型列表，空则查全部
     */
    List<BidFactRespDTO> getFacts(Long tenantId, List<String> types);
}
