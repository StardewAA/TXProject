package cn.iocoder.txgy.module.bidding.api.dto.bidagent;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 项目上下文（§3.1 GET /context/{projectId}）：项目事实卡 + 招标文件解析 + 全局事实
 */
@Data
public class BidContextRespDTO {

    /** 项目事实卡（工期/质量目标/报价策略/关键约束） */
    private Map<String, Object> projectFacts;

    /** 招标文件解析结果 */
    private Map<String, Object> tenderParse;

    /** 全局事实清单 */
    private List<BidFactRespDTO> globalFacts;
}
