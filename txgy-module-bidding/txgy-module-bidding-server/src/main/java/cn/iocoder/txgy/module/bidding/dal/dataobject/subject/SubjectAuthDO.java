package cn.iocoder.txgy.module.bidding.dal.dataobject.subject;
// [依据] 平台授权名单（管理端“已授权投标人/招标人”）：需求“仅授权投标人尚不充分，被授权后仍须完善信息”；
//       授权=平台对“某企业租户具备某主体角色”的核发（可停用/复用）。G 轨（平台级，跨租户可读，带 tenant_id 列手动过滤）。

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 主体授权记录（G 轨，平台管理端维护）。表：bidding_subject_auth（DDL 见 sql/2026-09-09-subject.sql）。
 */
@TableName("bidding_subject_auth")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubjectAuthDO extends BaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 被授权企业租户编号（一企业一租户） */
    private Long tenantId;
    /** 企业名称快照（授权操作时点取值，供管理端列表免跨系统联查；可更新） */
    private String tenantName;
    /** 主体类型：BIDDER/TENDERER */
    private String subjectType;
    /** 授权状态：ENABLED/DISABLED（SubjectAuthStatusEnum；撤销授权=DISABLED，保留留痕） */
    private String authStatus;
    /** 授权操作人（AdminUser id） */
    private Long authorizeUserId;
    /** 授权时间 */
    private LocalDateTime authorizeTime;
    /** 停用（撤销）时间 */
    private LocalDateTime disableTime;
    /** 备注 */
    private String remark;
}
