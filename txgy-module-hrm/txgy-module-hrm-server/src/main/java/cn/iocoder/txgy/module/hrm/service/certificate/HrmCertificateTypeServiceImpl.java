package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateTypeMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_CODE_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_DISABLED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_NAME_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_SPECIAL_OPERATION_REVIEW_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_USED;

/**
 * HRM 证件类型 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmCertificateTypeServiceImpl implements HrmCertificateTypeService {

    @Resource
    private HrmCertificateTypeMapper certificateTypeMapper;
    @Resource
    private HrmEmployeeCertificateMapper certificateMapper;

    @Override
    public Long createCertificateType(HrmCertificateTypeSaveReqVO reqVO) {
        // 1. 校验名称与编码不重复
        validateNameUnique(reqVO.getName(), null);
        validateCodeUnique(reqVO.getCode(), null);
        // 2. 校验特种作业必须复审
        validateSpecialOperationConfig(reqVO.getSpecialOperation(), reqVO.getNeedReview(),
                reqVO.getReviewCycleMonths());
        // 3. 插入
        HrmCertificateTypeDO certificateType = BeanUtils.toBean(reqVO, HrmCertificateTypeDO.class);
        certificateTypeMapper.insert(certificateType);
        return certificateType.getId();
    }

    @Override
    public void updateCertificateType(HrmCertificateTypeSaveReqVO reqVO) {
        // 1. 校验存在
        validateCertificateTypeExists(reqVO.getId());
        validateNameUnique(reqVO.getName(), reqVO.getId());
        validateCodeUnique(reqVO.getCode(), reqVO.getId());
        validateSpecialOperationConfig(reqVO.getSpecialOperation(), reqVO.getNeedReview(),
                reqVO.getReviewCycleMonths());
        // 2. 更新
        certificateTypeMapper.updateById(BeanUtils.toBean(reqVO, HrmCertificateTypeDO.class));
        // 3. 关闭复审时清空复审周期，避免残留旧值造成口径歧义
        if (!Boolean.TRUE.equals(reqVO.getNeedReview())) {
            certificateTypeMapper.update(null, new LambdaUpdateWrapper<HrmCertificateTypeDO>()
                    .eq(HrmCertificateTypeDO::getId, reqVO.getId())
                    .set(HrmCertificateTypeDO::getReviewCycleMonths, null));
        }
    }

    @Override
    public void deleteCertificateType(Long id) {
        // 1. 校验存在
        validateCertificateTypeExists(id);
        // 2. 已被台账引用时不允许删除
        if (certificateMapper.selectCountByTypeId(id) > 0) {
            throw exception(CERTIFICATE_TYPE_USED);
        }
        certificateTypeMapper.deleteById(id);
    }

    @Override
    public HrmCertificateTypeDO getCertificateType(Long id) {
        return certificateTypeMapper.selectById(id);
    }

    @Override
    public List<HrmCertificateTypeDO> getCertificateTypeList(Integer status) {
        return certificateTypeMapper.selectListByStatus(status);
    }

    @Override
    public PageResult<HrmCertificateTypeDO> getCertificateTypePage(HrmCertificateTypePageReqVO reqVO) {
        return certificateTypeMapper.selectPage(reqVO);
    }

    @Override
    public HrmCertificateTypeDO validateCertificateTypeExists(Long id) {
        HrmCertificateTypeDO certificateType = id == null ? null : certificateTypeMapper.selectById(id);
        if (certificateType == null) {
            throw exception(CERTIFICATE_TYPE_NOT_EXISTS);
        }
        return certificateType;
    }

    @Override
    public HrmCertificateTypeDO validateCertificateTypeEnabled(Long id) {
        HrmCertificateTypeDO certificateType = validateCertificateTypeExists(id);
        if (!CommonStatusEnum.isEnable(certificateType.getStatus())) {
            throw exception(CERTIFICATE_TYPE_DISABLED, certificateType.getName());
        }
        return certificateType;
    }

    @Override
    public Map<Long, HrmCertificateTypeDO> getCertificateTypeMap(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        List<HrmCertificateTypeDO> list = certificateTypeMapper.selectListByIds(ids);
        return convertMap(list, HrmCertificateTypeDO::getId);
    }

    /**
     * 证件类型名称在同一租户内不允许重复
     */
    private void validateNameUnique(String name, Long id) {
        HrmCertificateTypeDO same = certificateTypeMapper.selectByName(name);
        if (same != null && !Objects.equals(same.getId(), id)) {
            throw exception(CERTIFICATE_TYPE_NAME_DUPLICATE);
        }
    }

    /**
     * 证件类型编码在同一租户内不允许重复，编码为空时不校验
     */
    private void validateCodeUnique(String code, Long id) {
        if (code == null || code.isEmpty()) {
            return;
        }
        HrmCertificateTypeDO same = certificateTypeMapper.selectByCode(code);
        if (same != null && !Objects.equals(same.getId(), id)) {
            throw exception(CERTIFICATE_TYPE_CODE_DUPLICATE);
        }
    }

    /**
     * 特种作业证件必须开启复审并配置复审周期，否则无法生成复审预警
     */
    private void validateSpecialOperationConfig(Boolean specialOperation, Boolean needReview,
                                                Integer reviewCycleMonths) {
        if (Boolean.TRUE.equals(specialOperation)
                && (!Boolean.TRUE.equals(needReview) || reviewCycleMonths == null)) {
            throw exception(CERTIFICATE_TYPE_SPECIAL_OPERATION_REVIEW_REQUIRED);
        }
    }

}
