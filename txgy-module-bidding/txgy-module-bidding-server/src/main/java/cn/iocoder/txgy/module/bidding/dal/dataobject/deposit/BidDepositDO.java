package cn.iocoder.txgy.module.bidding.dal.dataobject.deposit;

// 规范依据：《招投标模块开发规范》§5 数据层（T 轨租户隔离）；《电子招标投标全流程说明书》§7
//   + 《实施条例》第26条（投标保证金不超过估算价2%）、第57/58条（履约保证金/退还时限）
// 关联方式：bid_section_code 定位标段拿估算价；subject_id 定位投标主体；file_id 为缴纳凭证

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 投标保证金登记。表：bidding_bid_deposit。
 *
 * <p>业务目标：登记投标人缴纳的投标保证金，登记时校验金额 ≤ 估算价 2%（条例第26条）；
 * 作为合同签订后 5 日内退还（条例第58条）的事实基础。
 */
@TableName("bidding_bid_deposit")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidDepositDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 标段编号（BID_SECTION_CODE） */
    private String bidSectionCode;
    /** 招标项目编号（TENDER_PROJECT_CODE，冗余便于按项目汇总） */
    private String tenderProjectCode;
    /** 投标主体ID（bidding_subject.id） */
    private Long subjectId;
    /** 保证金金额（元） */
    private BigDecimal amount;
    /** 币种（CURRENCY_CODE） */
    private String currencyCode;
    /** 缴纳方式（MARGIN_PAY_TYPE 字典） */
    private String payType;
    /** 缴纳状态（DEPOSIT_STATUS 字典） */
    private String status;
    /** 缴纳凭证文件ID（FileApi） */
    private Long fileId;
    /** 缴纳时间 */
    private LocalDateTime depositTime;
    /** 退还时间 */
    private LocalDateTime refundTime;
    /** 退还状态（字典） */
    private String refundStatus;
    /** 备注 */
    private String remark;
    /** 是否超期未退还（条例第58条：合同签订后5日内退还；@TableField(exist=false) 计算字段，不落库） */
    @TableField(exist = false)
    private Boolean refundOverdue;
}
