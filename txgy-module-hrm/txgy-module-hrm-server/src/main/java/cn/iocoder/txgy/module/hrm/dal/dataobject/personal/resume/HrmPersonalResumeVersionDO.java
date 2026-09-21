package cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 个人简历版本 DO。
 *
 * <p>求职者的在线简历是可随时编辑的草稿；本表保存的是「已固化」的不可变版本。
 * 版本一旦创建就不再原地修改，AI 分析与投递都以固定版本为输入，避免后续编辑影响历史结论。</p>
 */
@TableName("hrm_personal_resume_version")
@KeySequence("hrm_personal_resume_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmPersonalResumeVersionDO extends TenantBaseDO {

    /** 创建方式：手动固化当前草稿 */
    public static final int CREATE_TYPE_MANUAL = 1;
    /** 创建方式：确认优化建议后生成 */
    public static final int CREATE_TYPE_AGENT_OPTIMIZED = 2;

    /** 状态：有效 */
    public static final int STATUS_ACTIVE = 0;
    /** 状态：已失效 */
    public static final int STATUS_INVALID = 1;

    @TableId
    private Long id;
    /** 登录用户编号 */
    private Long userId;
    /** 父版本编号，首个版本为空 */
    private Long parentVersionId;
    /** 用户维度递增的版本号 */
    private Integer versionNo;
    /** 来源个人简历编号（草稿） */
    private Long sourceResumeId;
    /** 来源目标职位编号 */
    private Long sourcePostId;
    /** 创建方式：1 手动固化，2 确认优化建议 */
    private Integer createType;
    /** 修改摘要 */
    private String changeSummary;
    /** 内容结构版本 */
    private String schemaVersion;
    /** 结构化简历内容（JSON） */
    private String content;
    /** 内容指纹，用于幂等与追溯 */
    private String contentHash;
    /** 幂等键，同一用户重复提交不生成多个版本 */
    private String idempotencyKey;
    /** 状态：0 有效，1 已失效 */
    private Integer status;
}
