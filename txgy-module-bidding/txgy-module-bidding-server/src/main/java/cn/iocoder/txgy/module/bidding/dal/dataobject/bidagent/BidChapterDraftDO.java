package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 章节草稿 DO（按 (project_id, node_id, version) 幂等）
 */
@TableName("bidding_bid_chapter_draft")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class BidChapterDraftDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 项目编号
     */
    private Long projectId;

    /**
     * 章节节点编号
     */
    private String nodeId;

    /**
     * 章节正文
     */
    private String content;

    /**
     * 溯源引用块编号，逗号分隔
     */
    private String refs;

    /**
     * 状态：draft / review / confirmed
     */
    private String status;

    /**
     * 版本号
     */
    private Integer version;
}
