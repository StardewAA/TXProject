package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;

import java.util.List;
import java.util.Map;

/**
 * HRM 证照类型 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmCertificateTypeService {

    Long createCertificateType(HrmCertificateTypeSaveReqVO reqVO);

    void updateCertificateType(HrmCertificateTypeSaveReqVO reqVO);

    void deleteCertificateType(Long id);

    HrmCertificateTypeDO getCertificateType(Long id);

    HrmCertificateTypeDO validateCertificateTypeExists(Long id);

    List<HrmCertificateTypeDO> getCertificateTypeList();

    PageResult<HrmCertificateTypeDO> getCertificateTypePage(HrmCertificateTypePageReqVO reqVO);

    /**
     * 获得「类型编号 → 类型」映射（台账/预警回填用）
     */
    Map<Long, HrmCertificateTypeDO> getCertificateTypeMap();

}
