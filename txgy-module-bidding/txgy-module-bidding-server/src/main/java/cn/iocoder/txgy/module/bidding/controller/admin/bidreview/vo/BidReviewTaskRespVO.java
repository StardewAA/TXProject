package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 智慧标书审查任务出参（个人端只回本人数据；不返回他人信息）。 */
@Data
public class BidReviewTaskRespVO {

    private Long id;
    private String taskNo;
    private String taskName;
    private String sourceScene;
    private Long ownerUserId;
    private String ownerName;
    private Long projectId;
    private Long tenderProjectId;
    private Long ruleSetId;
    private String ruleSetName;
    private Long tenderFileId;
    private String tenderFileName;
    private Long bidFileId;
    private String bidFileName;
    private String parseMode;
    private String status;
    private String currentStep;
    private Integer progress;
    private String verdict;
    private BigDecimal scoreTotal;
    private Integer ruleCnt;
    private Integer issueCnt;
    private Integer highIssueCnt;
    private Boolean aiCalled;
    private String errorCode;
    private String errorMsg;
    private Integer retryCount;
    private Long costMs;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    /** 分项得分（报告有值时回填） */
    private List<ScoreItem> scoreItems;
    /** 是否已生成报告 */
    private Boolean reportReady;
    /** 是否废标（命中高危项即 true） */
    private Boolean rejected;
    /** 废标原因（命中高危的规则名称清单） */
    private java.util.List<String> rejectReasons;

    /** 分项得分 */
    @Data
    public static class ScoreItem {
        private String category;
        private BigDecimal score;
        private BigDecimal scoreMax;
    }
}
