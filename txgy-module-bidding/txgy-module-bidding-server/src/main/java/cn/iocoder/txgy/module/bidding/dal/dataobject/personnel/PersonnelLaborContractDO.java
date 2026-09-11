package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工劳动合同。表：personnel_labor_contract（DDL sql/history/2026-09-13-personnel-qualification.sql）。
 *
 * <p>规范出处：用户业务口径"核验劳动合同主体是否与申报企业完全一致"；《中华人民共和国劳动合同法》第十条
 * （建立劳动关系应当订立书面劳动合同）；《建筑业企业资质管理规定》（住建部令第22号）人员指标须为本企业员工。
 * <p>为什么这样写：社保可能由关联公司代缴（集团统一缴纳），单看社保会误判，故劳动合同独立成表，
 * 与社保一起构成"劳动关系双证据"，核验时两项都要过。
 * <p>业务目标：投标/申报前把"人证分离"拦在提交之前。
 */
@TableName("personnel_labor_contract")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelLaborContractDO extends TenantBaseDO {

    private Long id;
    /** 用人单位主体 id（一致性核验锚点） */
    private Long entId;
    private Long personId;
    private String contractNo;
    /** 合同类型 code：FIXED/OPEN/COMPLETE */
    private String contractType;
    private LocalDate signDate;
    private LocalDate startDate;
    /** 终止日（NULL=无固定期限） */
    private LocalDate endDate;
    /** 合同状态 code：ACTIVE/EXPIRED/TERMINATED */
    private String contractStatus;
    private String positionName;
    private Long fileId;
    private String remark;
    @Version
    private Integer version;
}
