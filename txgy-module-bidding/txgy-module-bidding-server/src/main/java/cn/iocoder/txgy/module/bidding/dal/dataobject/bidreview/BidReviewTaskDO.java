package cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智慧标书审查任务（表 bidding_bid_review_task）。
 *
 * <p>方案依据：docs/模块设计与规范/智慧标书审查-设计方案.md §3（领域模型）；
 * 规范依据：《招投标模块开发规范》§4.5（乐观锁/幂等）、§5（T 轨、文件只存 file_id）。
 *
 * <p>设计遵循/针对：① 一次上传 = 一个任务，状态机 QUEUED→RUNNING→SUCCESS/FAILED/CANCELED；
 * ② 结论（verdict）与状态解耦——SUCCESS 只代表"跑完了"；③ owner_user_id 是个人端隔离的唯一依据
 * （个人端一律 getLoginUserId() 写入/过滤，入参不收 userId，防越权）。
 */
@TableName("bidding_bid_review_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewTaskDO extends TenantBaseDO {

    private Long id;
    /** 任务编号（业务唯一键，形如 BR20260916000001） */
    private String taskNo;
    /** 任务名称（默认取标书文件名） */
    private String taskName;
    /** 来源场景：ADMIN_TASK / PORTAL_SELF */
    private String sourceScene;
    /** 归属人（个人端即本人；后端强制按此列隔离） */
    private Long ownerUserId;
    /** 归属人名称快照 */
    private String ownerName;
    /** 关联投标项目 id（可空） */
    private Long projectId;
    /** 关联招标项目 id（可空） */
    private Long tenderProjectId;
    /** 采用的规则集 id */
    private Long ruleSetId;
    /** 招标文件 file_id（可空：不传则只用平台规则库） */
    private Long tenderFileId;
    /** 招标文件访问地址快照（仅作解析取流定位；一致性以 file_id + sha256 为准） */
    private String tenderFileUrl;
    /** 招标文件名快照 */
    private String tenderFileName;
    /** 招标文件内容哈希（规则集复用依据） */
    private String tenderFileSha256;
    /** 投标文件（标书）file_id */
    private Long bidFileId;
    /** 标书访问地址快照（仅作解析取流定位） */
    private String bidFileUrl;
    /** 标书文件名快照 */
    private String bidFileName;
    /** 标书内容哈希（相同文件复用审查结果） */
    private String bidFileSha256;
    /** 解析方式：TEXT / EMPTY（无文字层=扫描件） */
    private String parseMode;
    /** 状态：QUEUED/RUNNING/SUCCESS/FAILED/CANCELED */
    private String status;
    /** 当前步骤：PARSE/RULE/GENERATE/REPORT */
    private String currentStep;
    /** 进度 0~100 */
    private Integer progress;
    /** 结论：PASS/RISK/FAIL（仅 SUCCESS 时有值） */
    private String verdict;
    /** 自评总分（AI 预估，报告需标注） */
    private BigDecimal scoreTotal;
    /** 分项得分 JSON */
    private String scoreJson;
    /** 参与比对的规则条数 */
    private Integer ruleCnt;
    /** 问题条数 */
    private Integer issueCnt;
    /** 高危问题条数 */
    private Integer highIssueCnt;
    /** 是否发生过模型调用（扫描件失败时应为 false——用于验证"不浪费 token"） */
    private Boolean aiCalled;
    /** 失败错误码（ErrorCodeConstants 名称） */
    private String errorCode;
    /** 失败原因（面向用户的短文案） */
    private String errorMsg;
    /** 重试次数 */
    private Integer retryCount;
    /** 开始执行时间 */
    private LocalDateTime startedTime;
    /** 完成时间 */
    private LocalDateTime finishedTime;
    /** 总耗时（毫秒） */
    private Long costMs;
    /** 备注（个人端可填，管理端可见） */
    private String remark;
    /** 乐观锁版本 */
    @Version
    private Integer version;
    /** 幂等键（一次点击一个键；重复提交只产生一个任务） */
    private String idempotentKey;
}
