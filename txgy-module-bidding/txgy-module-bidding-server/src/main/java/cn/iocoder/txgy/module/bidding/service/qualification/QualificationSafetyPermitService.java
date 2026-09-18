package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;

import java.time.LocalDate;

/**
 * 安全生产许可证服务（15-8：建档 + 状态机）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-8（permit_no UK、permit_status 状态机、
 * valid_to 并入 QUAL_EXPIRY 扫描）。
 */
public interface QualificationSafetyPermitService {

    /** 建档：permit_no UK 判重；默认 VALID；可关联 certificate_info 影像（存在性校验）。 */
    Long createPermit(Long entId, String permitNo, String issuingAuthority, LocalDate issueDate,
                      LocalDate validTo, String legalPerson, Long bindedCertId, String remark);

    /** 状态流转（SafetyPermitStatusEnum 迁移表校验 + version CAS）。 */
    void changeStatus(Long permitId, String targetStatus, Integer version, String reason);
}
