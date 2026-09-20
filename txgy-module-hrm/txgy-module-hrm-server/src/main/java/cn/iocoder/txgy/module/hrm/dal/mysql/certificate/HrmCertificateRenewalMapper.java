package cn.iocoder.txgy.module.hrm.dal.mysql.certificate;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateRenewalDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmCertificateRenewalMapper extends BaseMapperX<HrmCertificateRenewalDO> {

    default List<HrmCertificateRenewalDO> selectListByCertificateId(Long certificateId) {
        return selectList(new LambdaQueryWrapperX<HrmCertificateRenewalDO>()
                .eq(HrmCertificateRenewalDO::getCertificateId, certificateId)
                .orderByDesc(HrmCertificateRenewalDO::getId));
    }

    default List<HrmCertificateRenewalDO> selectListByCertificateIds(Collection<Long> certificateIds) {
        return selectList(new LambdaQueryWrapperX<HrmCertificateRenewalDO>()
                .in(HrmCertificateRenewalDO::getCertificateId, certificateIds)
                .orderByDesc(HrmCertificateRenewalDO::getId));
    }

    default List<HrmCertificateRenewalDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmCertificateRenewalDO>()
                .eq(HrmCertificateRenewalDO::getEmployeeId, employeeId)
                .orderByDesc(HrmCertificateRenewalDO::getId));
    }

}
