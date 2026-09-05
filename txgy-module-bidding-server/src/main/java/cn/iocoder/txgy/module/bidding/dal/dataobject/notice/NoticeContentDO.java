package cn.iocoder.txgy.module.bidding.dal.dataobject.notice;
// 规范依据：《招投标模块开发规范》五章三§2（内容表结构/只追加）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公告/公示正文内容（版本化，只追加不更新）。表：bidding_notice_content。
 * 结构与追加规则见《招投标模块开发规范》第五章第三节第 2 款。
 */
@TableName("bidding_notice_content")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeContentDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 归属公告主档 ID */
    private Long noticeId;
    /** 内容类型编码（同一公告下多类正文时区分，正文主体固定 BODY） */
    private String contentType;
    /** 正文全文 */
    private String content;
    /** 内容哈希（SHA-256，防篡改与去重） */
    private String sha256;
    /** 版本号（同公告同内容类型下自增、只追加） */
    private Integer version;

}
