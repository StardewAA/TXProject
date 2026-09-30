package cn.iocoder.txgy.module.bidding.dal.dataobject.prequal;

// 规范依据：《招投标模块开发规范》§5 数据层；《电子招标投标全流程说明书》§6
//   + 《实施条例》第15/16/17/18/19条（资格预审公告、发售≥5日、申请期≥5日、资格审查委员会、结果通知）
// 关联方式：prequal_code 内部编号；tender_project_code + bid_section_code 定位招标范围

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 资格预审主记录（招标方发起）。表：bidding_prequal。
 *
 * <p>业务目标：承载资格预审的公告、文件、申请期与状态；投标方据此提交资格预审申请
 * （bidding_prequal_apply），资格审查委员会审查后发出结果通知书。
 * 入口判断：招标项目主档的资格审查方式（qulificationCheckMode）为资格预审时发起。
 */
@TableName("bidding_prequal")
@Data
@EqualsAndHashCode(callSuper = true)
public class PrequalDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 资格预审编号（内部编号，前缀 P） */
    private String prequalCode;
    /** 招标项目编号（TENDER_PROJECT_CODE） */
    private String tenderProjectCode;
    /** 标段编号（BID_SECTION_CODE，空=招标项目级资格预审） */
    private String bidSectionCode;
    /** 资格预审项目名称 */
    private String title;
    /** 关联资格预审公告编号（bidding_notice.notice_code） */
    private String noticeCode;
    /** 资格预审文件 FileApi 主键（一致性依据） */
    private Long fileId;
    /** 申请开始时间（资格预审文件停止发售时间；与申请截止间隔须≥5日，条例第17条） */
    private LocalDateTime applyStartTime;
    /** 申请截止时间（提交资格预审申请文件截止） */
    private LocalDateTime applyDeadline;
    /** 状态（PrequalStatusEnum：DRAFT/PUBLISHED/APPLYING/REVIEWING/RESULTED/CLOSED） */
    private String status;
    /** 备注 */
    private String remark;
}
