package cn.iocoder.txgy.module.bidding.dal.dataobject.project;

// [规范依据]《招投标模块开发规范》§5 数据层 + 数据规范 V2.0 数据集 BidSectionVO（标段(包)）
// [业务目标] 标段主档：承载 bid_section_code（公告 BID_SECTION_CODES、开标/结果/截止时间查询以其定位），并落估算价/资格条件/工期等
// [为什么] 标段是投标流程的最小参与单元（投标人报名的粒度），此前仅以公告里的编号串存在，无法按标段汇总

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 标段（包）主档（数据集 BidSectionVO）。
 */
@TableName("bidding_bid_section")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidSectionDO extends TenantBaseDO {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 标段（包）编号（BID_SECTION_CODE；租户内唯一） */
    private String bidSectionCode;
    /** 所属招标项目编号（TENDER_PROJECT_CODE） */
    private String tenderProjectCode;
    /** 标段（包）名称（BID_SECTION_NAME） */
    private String bidSectionName;
    /** 统一交易标识码（UNIFIED_DEAL_CODE） */
    private String unifiedDealCode;
    /** 标段（包）序号（BID_ECTION_NUM；规范原文拼写照录） */
    private String bidEctionNum;
    /** 招标方式（BID_TENDER_MODE） */
    private String bidTenderMode;
    /** 行政区划（REGION_CODE） */
    private String regionCode;
    /** 交易场地代码（TRA_PLACE_CODE） */
    private String traPlaceCode;
    /** 标段（包）内容（BID_SECTION_CONTENT） */
    private String bidSectionContent;
    /** 标段（包）类型（BID_SECTION_TYPE） */
    private String bidSectionType;
    /** 合同估算价（CONTRACT_RECKON_PRICE，按数据集保真为字符串） */
    private String contractReckonPrice;
    /** 币种（CURRENCY_CODE） */
    private String currencyCode;
    /** 价格单位（PRICE_UNIT） */
    private String priceUnit;
    /** 工期/服务期（TIME_LIMIT） */
    private String timeLimit;
    /** 投标人资格条件（BID_QUALIFICATION） */
    private String bidQualification;
    /** 是否电子招投标（IS_WEB_BIDDING） */
    private String isWebBidding;
    /** 是否不见面开标（IS_NOMEET_BIDDING） */
    private String isNomeetBidding;
    /** 内部管理态：ACTIVE/CLOSED */
    private String state;
    /** BidSectionVO 全量字段快照 */
    private String fieldsJson;
    /** 备注 */
    private String remark;
}
