package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * 企业事实 DO（证照 / 业绩 / 人员 / 财务），COPY_FACT 通道的唯一数据源
 */
@TableName("bidding_bid_enterprise_fact")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class BidEnterpriseFactDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 类型：LICENSE/QUAL/PERFORMANCE/PERSON/FINANCE/PLAN/PRICE 等
     */
    private String type;

    /**
     * 事实名称
     */
    private String name;

    /**
     * 事实值
     */
    private String value;

    /**
     * 单位
     */
    private String unit;

    /**
     * 有效期
     */
    private LocalDate validUntil;

    /**
     * 来源文档名
     */
    private String sourceDocName;

    /**
     * 溯源块编号，逗号分隔
     */
    private String sourceBlockIds;
}
