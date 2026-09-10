package cn.iocoder.txgy.module.bidding.dal.dataobject.qualification;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 企业资质标准库（核查依据，可版本化）。表：qualification_standard（DDL sql/2026-09-09-…sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-2：std_version + 序列/类别/等级适用范围、
 * 四要素指标 JSON（req_staff 必填）、生效区间与 source_ref（国标表110/111 依据）。
 * <p>设计遵循/针对：指标列 JSON 字符串存储（开发规范 §5 三-7），核查结论据此复算；生效区间参与“现行版本”选取。
 */
@TableName("qualification_standard")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationStandardDO extends TenantBaseDO {

    private Long id;
    /** 标准版本（如“建市〔2014〕159号+2021 修订”） */
    private String stdVersion;
    private String qualSerial;
    private String qualCategory;
    private String qualGrade;
    /** 资产指标 JSON（如 net_asset_min） */
    private String reqAssets;
    /** 人员指标 JSON（必填：reg_builder/mid_title/skilled 等） */
    private String reqStaff;
    /** 业绩指标 JSON */
    private String reqPerformance;
    /** 装备指标 JSON */
    private String reqEquipment;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    /** 依据文号/国标来源 */
    private String sourceRef;
}
