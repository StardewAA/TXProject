package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 招投标项目 DO
 */
@TableName("bidding_bid_project")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class BidProjectDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目事实卡（JSON：工期/质量目标/报价策略/关键约束）
     */
    private String factsJson;

    /**
     * 招标文件解析结果（JSON：废标条款/关键约束/全局事实）
     */
    private String tenderParseJson;

    /**
     * 标书章节树（Node 编排回写，JSON：大纲节点 + 承接来源）
     */
    private String outlineJson;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
