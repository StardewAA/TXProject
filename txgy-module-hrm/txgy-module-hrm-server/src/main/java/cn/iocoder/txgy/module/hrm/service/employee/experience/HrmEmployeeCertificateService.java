package cn.iocoder.txgy.module.hrm.service.employee.experience;

import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.certificate.HrmEmployeeCertificateSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * HRM 员工证书 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeCertificateService {

    /**
     * 创建员工证书
     *
     * @param reqVO 证书信息
     * @return 证书编号
     */
    Long createCertificate(@Valid HrmEmployeeCertificateSaveReqVO reqVO);

    /**
     * 更新员工证书
     *
     * @param reqVO 证书信息
     */
    void updateCertificate(@Valid HrmEmployeeCertificateSaveReqVO reqVO);

    /**
     * 删除员工证书
     *
     * @param id 证书编号
     */
    void deleteCertificate(Long id);

    /**
     * 校验员工证书是否存在
     *
     * @param id 证书编号
     * @return 员工证书
     */
    HrmEmployeeCertificateDO validateCertificateExists(Long id);

    /**
     * 获得员工证书列表
     *
     * @param employeeId 员工编号
     * @return 证书列表
     */
    List<HrmEmployeeCertificateDO> getCertificateListByEmployeeId(Long employeeId);

    /**
     * 证照台账分页（跨员工）
     */
    cn.iocoder.txgy.framework.common.pojo.PageResult<HrmEmployeeCertificateDO> getCertificateLedgerPage(
            cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateLedgerPageReqVO reqVO);

    /**
     * 证照台账列表（导出用）
     */
    List<HrmEmployeeCertificateDO> getCertificateLedgerList(
            cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateLedgerPageReqVO reqVO);

    /**
     * 到期预警清单（临期 + 已过期）
     *
     * @param warnDays 临期判定天数
     */
    List<HrmEmployeeCertificateDO> getExpireWarningList(Integer warnDays);

    /**
     * 证照核验（已核验必须已上传证件扫描件）
     *
     * @param id 证照编号
     * @param verifyStatus 核验状态：1 已核验，2 核验不通过
     */
    void verifyCertificate(Long id, Integer verifyStatus);

}
