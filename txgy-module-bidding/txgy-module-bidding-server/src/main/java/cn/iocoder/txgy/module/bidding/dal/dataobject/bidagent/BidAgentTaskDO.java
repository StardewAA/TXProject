package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 一键生成标书任务台账 DO（前台/后台共用；Node 状态经契约回写） */
@TableName("bidding_bid_agent_task")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BidAgentTaskDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 任务号（幂等键） */
    private String taskNo;

    private Long projectId;

    /** 标段编号（检索隔离口径） */
    private String bidSectionCode;

    /**
     * 标书类型：technical 技术标书 / business 商务标书（前台工坊选择，透传编排侧组织章节大纲）
     */
    private String docCategory;

    /** 招标文件材料编号 */
    private Long tenderMaterialId;

    /** 模板材料编号（可空） */
    private Long templateMaterialId;

    /** PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED */
    private String status;

    /** PARSE/OUTLINE/DRAFT/FILL */
    private String currentStep;

    /** 进度 0~100 */
    private Integer progress;

    /** 底座任务编号 */
    private String agentTaskId;

    private String stepDetailJson;

    private String openQuestionsJson;

    private Long artifactFileId;

    private String artifactUrl;

    private String errorCode;

    private String errorMessage;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String remark;
}
