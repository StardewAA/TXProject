package cn.iocoder.txgy.module.bidding.dal.dataobject.qualification;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 企业资质主档（T 轨 · qualification_ 前缀例外）。表：enterprise_qualification（DDL 见 sql/history/2026-09-07-…sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1（序列/类别/等级/cert_no UK、
 * qual_status 状态机、binded_cert_id 关联通用台账复用影像与验真）。
 * <p>设计遵循/针对：① code 存储（开发规范 §5 三-7）；② 主档只存资质事实，达标核查/申报落 15-6/15-7 表；
 * ③ version @Version 乐观锁，办结回写与状态流转 CAS（表 15-7 注释口径）；④ valid_to 驱动到期预警（M-later 并入扫描）。
 */
@TableName("enterprise_qualification")
@Data
@EqualsAndHashCode(callSuper = true)
public class EnterpriseQualificationDO extends TenantBaseDO {

    /** 主键（雪花 ID；=设计 qual_id） */
    private Long id;
    /** 关联企业主体 */
    private Long entId;
    /** 资质序列 code：CONSTRUCTION/SURVEY/DESIGN/SUPERVISION/TESTING */
    private String qualSerial;
    /** 资质类别（如“市政公用工程施工总承包”） */
    private String qualCategory;
    /** 资质等级（特级/一级/二级…，国标表110） */
    private String qualGrade;
    /** 资质证书编号（UK 组成） */
    private String certNo;
    /** 发证/许可机关 */
    private String issuingAuthority;
    /** 发证日期 */
    private LocalDate issueDate;
    /** 有效期至（NULL=长期） */
    private LocalDate validTo;
    /** 批准承包/承揽范围 */
    private String approvalScope;
    /** 资质状态 code（状态机见 QualificationStatusEnum） */
    private String qualStatus;
    /** 关联 certificate_info（影像/验真复用） */
    private Long bindedCertId;
    /** 数据来源 code */
    private String sourceChannel;
    /** 备注 */
    private String remark;
    /** 乐观锁版本（@Version） */
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
