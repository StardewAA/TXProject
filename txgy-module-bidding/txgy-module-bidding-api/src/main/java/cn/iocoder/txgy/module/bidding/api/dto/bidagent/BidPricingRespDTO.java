package cn.iocoder.txgy.module.bidding.api.dto.bidagent;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 报价数据响应（COPY_PRICING 通道数据源）。
 * <p>Node 侧按 bookmark-dict.json 的 table/row/col 把行数据展开为书签名取值：
 * generic 计价表（bill/measure/other/service/analysis/material/project_cost/unit_cost）按行序映射，
 * daywork 按「组别」分流，fee/cost_breakdown 按「费用项」名映射，shared 直接按书签基础名回填。
 */
@Data
public class BidPricingRespDTO {

    /** 共享变量：书签基础名 → 取值（投标总价/措施费/工期/质量标准/专业/日期等） */
    private Map<String, String> shared;

    /** 计价表数据行：表名 → 行列表（行内为 列名 → 文本值） */
    private Map<String, List<Map<String, String>>> tables;
}
