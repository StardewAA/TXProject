package cn.iocoder.txgy.module.bidding.api.dto.bidagent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 章节草稿回写请求（§3.1 POST /draft/chapter，按 (projectId, nodeId, version) 幂等）
 */
@Data
public class BidChapterDraftReqDTO {

    /** 租户编号 */
    private Long tenantId;

    /** 项目编号 */
    private Long projectId;

    /** 章节节点编号 */
    @NotBlank(message = "章节节点编号不能为空")
    private String nodeId;

    /** 章节正文 */
    @NotBlank(message = "章节正文不能为空")
    private String content;

    /** 溯源引用块编号 */
    private List<String> refs;

    /** 状态：draft / review / confirmed */
    private String status;

    /** 版本号，用于幂等，默认 1 */
    private Integer version;
}
