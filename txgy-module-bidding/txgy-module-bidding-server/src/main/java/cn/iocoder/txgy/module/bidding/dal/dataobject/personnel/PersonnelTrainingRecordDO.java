package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工培训记录。表：personnel_training_record（DDL sql/history/2026-09-13-personnel-qualification.sql）。
 *
 * <p>规范出处：用户业务口径"记录员工基本信息、资质证书、培训记录等"；《电子证件全流程管理_数据库设计.md》
 * v3.3 表 15-3（edu_hours/edu_hours_required 继续教育学时=达标要素之一）。
 * <p>为什么这样写：继续教育学时是注册类证书延续的硬条件，必须按次留痕（次数/学时/有效期），
 * 故独立成表而非在证书上放一个学时数字。
 * <p>业务目标：培训与继续教育可追溯，作为资质人员指标与安全生产教育的佐证。
 */
@TableName("personnel_training_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelTrainingRecordDO extends TenantBaseDO {

    private Long id;
    private Long entId;
    /** 参训员工 */
    private Long personId;
    private String trainingName;
    /** 培训类型 code：SAFETY/CONTINUE_EDU/SKILL/PRE_JOB/OTHER */
    private String trainingType;
    private String organizer;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 学时（继续教育达标要素） */
    private BigDecimal eduHours;
    /** 结果 code：QUALIFIED/UNQUALIFIED/ATTENDING */
    private String result;
    private BigDecimal score;
    private String certNo;
    /** 培训合格证有效期止 */
    private LocalDate certValidTo;
    private Long fileId;
    private String remark;
    @Version
    private Integer version;
}
