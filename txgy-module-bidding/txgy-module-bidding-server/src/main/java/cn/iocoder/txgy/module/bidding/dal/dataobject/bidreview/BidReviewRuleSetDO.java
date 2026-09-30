package cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智慧标书审查规则集（表 bidding_bid_review_rule_set）。
 *
 * <p>方案依据：§2.2（规则双轨：平台规则库 + 招标文件解析结果）、§3（领域模型）。
 * <p>设计遵循/针对：平台规则集 default_flag=true 供个人端"不传招标文件"时兜底；
 * 招标文件解析出的规则集带 tender_file_sha256，同一份招标文件不重复解析（省 token）。
 */
@TableName("bidding_bid_review_rule_set")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewRuleSetDO extends TenantBaseDO {

    private Long id;
    /** 规则集编码（业务唯一键） */
    private String ruleSetCode;
    /** 规则集名称 */
    private String ruleSetName;
    /** 来源：PLATFORM / TENDER_DOC / MIXED */
    private String source;
    /** 招标文件 file_id（source=TENDER_DOC 时有值） */
    private Long tenderFileId;
    /** 招标文件哈希 */
    private String tenderFileSha256;
    /** 解析状态：SUCCESS/FAILED/PENDING */
    private String parseStatus;
    /** 规则条数 */
    private Integer ruleCnt;
    /** 是否启用 */
    private Boolean enable;
    /** 是否平台默认规则集 */
    private Boolean defaultFlag;
    /** 说明 */
    private String remark;
    /** 乐观锁版本 */
    @Version
    private Integer version;
}
