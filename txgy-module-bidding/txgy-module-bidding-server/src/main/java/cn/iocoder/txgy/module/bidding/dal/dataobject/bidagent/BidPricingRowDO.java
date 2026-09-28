package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（DO 与表一一对应、驼峰字段、@TableName）
// [业务目标] 报价数据结构化存储：报价 Excel（PRICING 材料角色）解析后按「表名 + 行号 + 列值 JSON」落库，
//           COPY_PRICING 通道（Node 契约接口 /contract/pricing）的唯一数据源
// [为什么] 计价表数据是投标人编制报价的产物，无法从招标文件/企业事实抽取；Excel 直接转 JSON 行，
//          由 Node 侧按 bookmark-dict.json 的 table/row/col 映射到书签名，Java 不感知书签细节

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("bidding_bid_pricing")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BidPricingRowDO extends TenantBaseDO {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 来源材料编号（按材料精确级联清理/重建） */
    private Long materialId;
    /** 所属项目 */
    private Long projectId;
    /** 计价表名：bill/measure/other/daywork/service/fee/analysis/material/project_cost/unit_cost/cost_breakdown/shared */
    private String tableName;
    /** 数据行号（从 1 起；shared 表为变量行号） */
    private Integer rowNo;
    /** 该行列值 JSON：{列名: 文本值} */
    private String dataJson;
}
