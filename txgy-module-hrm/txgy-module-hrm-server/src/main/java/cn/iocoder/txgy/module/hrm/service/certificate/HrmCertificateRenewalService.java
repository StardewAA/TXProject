package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal.HrmCertificateRenewalCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal.HrmCertificateRenewalRespVO;

import java.util.List;

/**
 * HRM 证件续证/复审 Service 接口
 *
 * 续证与复审只追加历史记录，不覆盖原证件：办理前的有效期与编号以快照形式保存在历史记录中，
 * 台账主表更新为办理后的新值，附件同样只追加新版本。
 *
 * @author OPENLAB BS
 */
public interface HrmCertificateRenewalService {

    /**
     * 办理续证/复审
     *
     * @param reqVO 办理信息
     * @return 续证/复审记录编号
     */
    Long createCertificateRenewal(HrmCertificateRenewalCreateReqVO reqVO);

    /**
     * 获得证件的续证/复审历史
     *
     * @param certificateId 证件台账编号
     * @return 续证/复审历史
     */
    List<HrmCertificateRenewalRespVO> getCertificateRenewalList(Long certificateId);

    /**
     * 获得员工的续证/复审历史
     *
     * @param employeeId 员工编号
     * @return 续证/复审历史
     */
    List<HrmCertificateRenewalRespVO> getCertificateRenewalListByEmployeeId(Long employeeId);

}
