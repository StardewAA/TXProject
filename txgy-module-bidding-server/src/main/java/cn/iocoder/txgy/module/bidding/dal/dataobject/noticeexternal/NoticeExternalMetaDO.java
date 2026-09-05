package cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal;
// 规范依据：设计文档 v0.6 §7/§15.1（G 轨）；《招投标模块开发规范》五章

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外部公告动态字段兜底（G 轨，1:1 可选）。表：bidding_notice_external_meta。
 * meta_json=原页面结构化字段全集（列不固定的兜底）；extra_raw=整页快照。
 */
@TableName("bidding_notice_external_meta")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeExternalMetaDO extends BaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 归属外部公告主档 ID */
    private Long noticeId;
    /** 原始页面结构化字段全集（JSON，兜底防丢） */
    private String metaJson;
    /** 整页快照（主档 extra_raw 分表冗余时用） */
    private String extraRaw;

}
