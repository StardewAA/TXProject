package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateTypeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_CODE_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_DISABLED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_NAME_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_NOT_EXISTS;

/**
 * HRM 证照类型 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmCertificateTypeServiceImpl implements HrmCertificateTypeService {

    @Resource
    private HrmCertificateTypeMapper certificateTypeMapper;

    @Override
    public Long createCertificateType(HrmCertificateTypeSaveReqVO reqVO) {
        if (certificateTypeMapper.selectByName(reqVO.getName()) != null) {
            throw exception(CERTIFICATE_TYPE_NAME_DUPLICATE);
        }
        if (certificateTypeMapper.selectByCode(reqVO.getCode()) != null) {
            throw exception(CERTIFICATE_TYPE_CODE_DUPLICATE);
        }
        HrmCertificateTypeDO type = BeanUtils.toBean(reqVO, HrmCertificateTypeDO.class);
        certificateTypeMapper.insert(type);
        return type.getId();
    }

    @Override
    public void updateCertificateType(HrmCertificateTypeSaveReqVO reqVO) {
        validateCertificateTypeExists(reqVO.getId());
        HrmCertificateTypeDO byName = certificateTypeMapper.selectByName(reqVO.getName());
        if (byName != null && !byName.getId().equals(reqVO.getId())) {
            throw exception(CERTIFICATE_TYPE_NAME_DUPLICATE);
        }
        HrmCertificateTypeDO byCode = certificateTypeMapper.selectByCode(reqVO.getCode());
        if (byCode != null && !byCode.getId().equals(reqVO.getId())) {
            throw exception(CERTIFICATE_TYPE_CODE_DUPLICATE);
        }
        certificateTypeMapper.updateById(BeanUtils.toBean(reqVO, HrmCertificateTypeDO.class));
    }

    @Override
    public void deleteCertificateType(Long id) {
        validateCertificateTypeExists(id);
        certificateTypeMapper.deleteById(id);
    }

    @Override
    public HrmCertificateTypeDO getCertificateType(Long id) {
        return certificateTypeMapper.selectById(id);
    }

    @Override
    public HrmCertificateTypeDO validateCertificateTypeExists(Long id) {
        HrmCertificateTypeDO type = certificateTypeMapper.selectById(id);
        if (type == null) {
            throw exception(CERTIFICATE_TYPE_NOT_EXISTS);
        }
        return type;
    }

    @Override
    public List<HrmCertificateTypeDO> getCertificateTypeList() {
        return certificateTypeMapper.selectListAll();
    }

    @Override
    public PageResult<HrmCertificateTypeDO> getCertificateTypePage(HrmCertificateTypePageReqVO reqVO) {
        return certificateTypeMapper.selectPage(reqVO);
    }

    @Override
    public Map<Long, HrmCertificateTypeDO> getCertificateTypeMap() {
        return certificateTypeMapper.selectListAll().stream()
                .collect(Collectors.toMap(HrmCertificateTypeDO::getId, item -> item, (a, b) -> a));
    }

    /**
     * 校验类型可用于新登记证照（存在且启用）
     */
    public HrmCertificateTypeDO validateUsable(Long id) {
        HrmCertificateTypeDO type = validateCertificateTypeExists(id);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(type.getStatus())) {
            throw exception(CERTIFICATE_TYPE_DISABLED, type.getName());
        }
        return type;
    }

}
