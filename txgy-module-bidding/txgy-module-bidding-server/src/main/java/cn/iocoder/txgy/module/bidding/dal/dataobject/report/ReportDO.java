package cn.iocoder.txgy.module.bidding.dal.dataobject.report;

// 规范依据：《招投标模块开发规范》§5 数据层（T 轨租户隔离）；《电子招标投标全流程说明书》§11.2
//   + 《招标投标法》第47条（确定中标人之日起 15 日内向有关行政监督部门提交书面报告）
// 关联方式：tender_project_code 定位招标项目；award_time = 中标结果公告（WIN_RESULT_PUBLICITY）发布时间（定标时间锚点）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定标书面报告。表：bidding_report。
 *
 * <p>业务目标：记录招标人向行政监督部门提交的招标投标情况书面报告（招标投标法第47条），
 * 台账以 reportOverdue 标记「定标后超 15 日未提交」（义务逾期提醒，不阻断后续流程）。
 */
@TableName("bidding_report")
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 招标项目编号（TENDER_PROJECT_CODE） */
    private String tenderProjectCode;
    /** 标段编号（BID_SECTION_CODE，空=项目级） */
    private String bidSectionCode;
    /** 报告编号（内部编号，前缀 R） */
    private String reportCode;
    /** 报告名称 */
    private String reportName;
    /** 报告文件 FileApi 主键 */
    private Long fileId;
    /** 提交时间 */
    private LocalDateTime submitTime;
    /** 定标时间（中标结果公告发布时间，招标投标法第47条 15 日时限起算点） */
    private LocalDateTime awardTime;
    /** 状态（SUBMITTED 已提交） */
    private String status;
    /** 备注 */
    private String remark;
    /** 是否超期未提交（招标投标法第47条：定标后 15 日内提交；@TableField(exist=false) 计算字段，不落库） */
    @TableField(exist = false)
    private Boolean reportOverdue;
}
