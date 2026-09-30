package cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智慧标书审查版本化内容（表 bidding_bid_review_content）。
 *
 * <p>规范依据：《招投标模块开发规范》§5.3 第 2 款——内容型文本（标书正文、AI 原始返回、
 * 报告 Markdown）一律版本化：每次变更追加新版本，旧版本只读保留，不覆盖、不物理删除。
 * <p>设计遵循/针对：重复"一键生成"不会覆盖历史，复核与追责有据可查。
 */
@TableName("bidding_bid_review_content")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewContentDO extends TenantBaseDO {

    private Long id;
    /** 业务记录 id（本域即 task_id） */
    private Long bizId;
    /** 内容类型：BID_TEXT/BID_STRUCT/TENDER_TEXT/AI_RAW/REPORT_MD */
    private String contentType;
    /** 正文内容 */
    private String content;
    /** 内容哈希 */
    private String sha256;
    /** 版本号（自增、只追加；非乐观锁列） */
    private Integer version;
}
