package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工档案（人员主数据，一人一档）。表：bidding_personnel_profile（DDL sql/2026-09-13-personnel-qualification.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（person_name/id_card_enc/id_card_blind_index/
 * is_full_time/reg_valid_to 等人员口径的"人"侧主档）；《招投标模块开发规范》§5 四-1（审计列由 BaseDO 提供）
 * 与五（T 轨继承 TenantBaseDO，租户条件由拦截器自动拼接，业务代码不手写）。
 * <p>为什么这样写：① 身份证等 L4 字段只存密文 + 盲索引（§4.8 密级分级），业务侧不落明文，判重走盲索引等值；
 * ② is_full_time 与 15-3 保持同一口径（仅 FULL_TIME 为真），保证"挂靠核查"两处判定一致；
 * ③ 身份证有效期（id_card_valid_to）单列，是用户明确要求的"身份证到期预警"扫描源。
 * <p>业务目标：把员工从 Excel 台账升级为可检索、可预警、可核验的人员主数据，作为证书/培训/社保/合同的挂载点。
 */
@TableName("bidding_personnel_profile")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelProfileDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 归属企业 */
    private Long entId;
    /** 员工编号（企业内部唯一） */
    private String personNo;
    /** 姓名（L3 脱敏展示） */
    private String personName;
    /** 身份证号密文（L4；业务侧不落明文） */
    private String idCardEnc;
    /** 身份证盲索引（HMAC，等值判重，不解密） */
    private String idCardBlindIndex;
    /** 身份证有效期止（身份证到期预警扫描源） */
    private LocalDate idCardValidTo;
    /** 性别 code：MALE/FEMALE/UNKNOWN */
    private String gender;
    private LocalDate birthDate;
    /** 手机（L3） */
    private String mobile;
    private String email;
    /** 学历层次 code（PersonnelEduLevelEnum） */
    private String eduLevel;
    /** 所学专业（人岗匹配度） */
    private String eduMajor;
    private String school;
    private LocalDate graduateDate;
    /** 职称等级 code（与 15-3 title_level 同口径） */
    private String titleLevel;
    /** 职称专业 */
    private String titleMajor;
    /** 参加工作时间（工作年限计算基准） */
    private LocalDate workStartDate;
    private LocalDate joinDate;
    private String deptName;
    private String positionName;
    /** 用工形式 code（PersonnelEmploymentTypeEnum；劳务派遣/兼职=挂靠风险） */
    private String employmentType;
    /** 是否本单位在职（与 15-3 同口径；仅 FULL_TIME 为真） */
    private Boolean isFullTime;
    /** 在职状态 code（PersonnelStatusEnum；仅 ACTIVE 可配证） */
    private String personnelStatus;
    /** 最近状态变更日期 */
    private LocalDate statusChangeDate;
    /** 关联平台登录用户（员工自助维护用，可空） */
    private Long bindedAuthUserId;
    private String archiveNo;
    private String remark;
    /** 乐观锁版本（开发规范 §4.5 并发覆盖防护） */
    @Version
    private Integer version;
}
