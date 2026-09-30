package cn.iocoder.txgy.module.bidding.dal.dataobject.prequal;

// 规范依据：《招投标模块开发规范》§5 数据层；《电子招标投标全流程说明书》§6
//   + 《实施条例》第19条（未通过资格预审的申请人不具有投标资格；通过者<3家应重新招标）
// 关联方式：prequal_id 归属资格预审；subject_id 归属投标主体；file_id 申请文件一致性依据

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 资格预审申请（投标方提交 + 资格审查委员会审查）。表：bidding_prequal_apply。
 *
 * <p>业务目标：投标方在申请截止前提交资格预审申请文件；资格审查委员会审查后
 * 判定通过/不通过；通过者取得投标资格（条例第19条）。
 */
@TableName("bidding_prequal_apply")
@Data
@EqualsAndHashCode(callSuper = true)
public class PrequalApplyDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 资格预审ID（bidding_prequal.id） */
    private Long prequalId;
    /** 资格预审编号（冗余，便于按编号追溯） */
    private String prequalCode;
    /** 招标项目编号（冗余） */
    private String tenderProjectCode;
    /** 标段编号（冗余） */
    private String bidSectionCode;
    /** 投标主体ID（bidding_subject.id） */
    private Long subjectId;
    /** 申请人用户ID */
    private Long userId;
    /** 资格预审申请文件 FileApi 主键 */
    private Long fileId;
    /** 申请提交时间 */
    private LocalDateTime applyTime;
    /** 状态（PrequalApplyStatusEnum：SUBMITTED/PASSED/FAILED） */
    private String status;
    /** 审查结果（PASSED/FAILED） */
    private String reviewResult;
    /** 审查说明/不通过原因 */
    private String reviewReason;
    /** 审查完成时间 */
    private LocalDateTime reviewTime;
    /** 备注 */
    private String remark;
}
