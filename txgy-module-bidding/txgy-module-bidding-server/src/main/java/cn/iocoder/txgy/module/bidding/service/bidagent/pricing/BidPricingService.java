package cn.iocoder.txgy.module.bidding.service.bidagent.pricing;

import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidPricingRespDTO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMaterialDO;

/**
 * 报价数据服务（PRICING 材料角色，COPY_PRICING 通道数据源）
 */
public interface BidPricingService {

    /**
     * 解析报价 Excel 并覆盖写入结构化行（按材料级联：先删后插）。
     *
     * @param material PRICING 材料（须带 fileUrl 与 projectId）
     * @return 写入的行数（shared 表每变量一行）
     * @throws RuntimeException 解析失败（Excel 结构/表头/文件格式问题），message 为专员可见原因
     */
    int parseAndStore(BidAgentMaterialDO material);

    /** 按材料级联删除报价行（删除材料/重建前调用） */
    void deleteByMaterial(Long materialId);

    /** 组装契约响应：shared 共享变量 + tables 计价表行 */
    BidPricingRespDTO getPricing(Long projectId, Long tenantId);
}
