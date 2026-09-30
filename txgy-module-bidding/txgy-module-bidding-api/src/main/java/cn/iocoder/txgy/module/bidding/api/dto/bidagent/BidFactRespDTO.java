package cn.iocoder.txgy.module.bidding.api.dto.bidagent;

import lombok.Data;

import java.util.List;

/**
 * 企业事实（证照 / 业绩 / 人员 / 财务），COPY_FACT 通道的唯一数据源
 */
@Data
public class BidFactRespDTO {

    /** 事实编号 */
    private String factId;

    /** 类型：LICENSE/QUAL/PERFORMANCE/PERSON/FINANCE/PLAN/PRICE 等 */
    private String type;

    /** 事实名称 */
    private String name;

    /** 事实值 */
    private String value;

    /** 单位 */
    private String unit;

    /** 有效期，格式 yyyy-MM-dd */
    private String validUntil;

    /** 来源文档名 */
    private String sourceDocName;

    /** 溯源块编号 */
    private List<String> sourceBlockIds;
}
