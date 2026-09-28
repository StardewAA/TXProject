package cn.iocoder.txgy.module.bidding.dal.dataobject.objection;

// 规范依据：《招投标模块开发规范》§5 数据层（T 轨租户隔离）；《电子招标投标全流程说明书》§6.3 第4条
//   + 《实施条例》第22条（资格预审文件异议申请截止2日前/招标文件异议投标截止10日前提出，招标人3日内答复，答复前暂停）
// 关联方式：tender_project_code + bid_section_code 定位；subject_id 定位提出异议的投标主体

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 投标异议登记。表：bidding_objection。
 *
 * <p>业务目标：登记投标人对资格预审文件/招标文件的异议，提出时校验截止时限
 * （资格预审文件异议申请截止前 2 日、招标文件异议投标截止前 10 日，条例第22条）；
 * 招标人 3 日内答复（答复前暂停），答复超期以 replyOverdue 标记。
 */
@TableName("bidding_objection")
@Data
@EqualsAndHashCode(callSuper = true)
public class ObjectionDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 招标项目编号（TENDER_PROJECT_CODE） */
    private String tenderProjectCode;
    /** 标段编号（BID_SECTION_CODE，异议落实到标段以对齐截止时限） */
    private String bidSectionCode;
    /** 异议类型（OBJECTION_TYPE：PREQUAL_FILE 资格预审文件 / TENDER_FILE 招标文件） */
    private String objectionType;
    /** 提出主体ID（bidding_subject.id） */
    private Long subjectId;
    /** 异议内容 */
    private String content;
    /** 提出时间 */
    private LocalDateTime proposeTime;
    /** 状态（PENDING 待答复 / REPLIED 已答复） */
    private String status;
    /** 答复时间 */
    private LocalDateTime replyTime;
    /** 答复内容 */
    private String replyContent;
    /** 备注 */
    private String remark;
    /** 答复是否超期（条例第22条：收到后 3 日内答复；@TableField(exist=false) 计算字段，不落库） */
    @TableField(exist = false)
    private Boolean replyOverdue;
}
