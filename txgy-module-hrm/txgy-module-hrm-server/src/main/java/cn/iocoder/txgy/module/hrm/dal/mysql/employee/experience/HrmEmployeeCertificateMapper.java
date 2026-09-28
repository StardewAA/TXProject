package cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmEmployeeCertificateMapper extends BaseMapperX<HrmEmployeeCertificateDO> {

    default List<HrmEmployeeCertificateDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .eq(HrmEmployeeCertificateDO::getEmployeeId, employeeId)
                .orderByAsc(HrmEmployeeCertificateDO::getSort)
                .orderByDesc(HrmEmployeeCertificateDO::getId));
    }

    default PageResult<HrmEmployeeCertificateDO> selectPage(HrmCertificateLedgerPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .likeIfPresent(HrmEmployeeCertificateDO::getName, reqVO.getName())
                .likeIfPresent(HrmEmployeeCertificateDO::getNo, reqVO.getNo())
                .eqIfPresent(HrmEmployeeCertificateDO::getEmployeeId, reqVO.getEmployeeId())
                .eqIfPresent(HrmEmployeeCertificateDO::getTypeId, reqVO.getTypeId())
                .eqIfPresent(HrmEmployeeCertificateDO::getStatus, reqVO.getStatus())
                .orderByAsc(HrmEmployeeCertificateDO::getSort)
                .orderByDesc(HrmEmployeeCertificateDO::getId));
    }

    /**
     * 查询纳入资格证件域管理（已配置证件类型）且未注销的证件，供每日状态与预警扫描使用
     */
    default List<HrmEmployeeCertificateDO> selectListForWarningScan() {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .isNotNull(HrmEmployeeCertificateDO::getTypeId)
                .ne(HrmEmployeeCertificateDO::getStatus, HrmCertificateStatusEnum.CANCELLED.getStatus())
                .orderByAsc(HrmEmployeeCertificateDO::getId));
    }

    default Long selectCountByTypeId(Long typeId) {
        return selectCount(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .eq(HrmEmployeeCertificateDO::getTypeId, typeId));
    }

    default List<HrmEmployeeCertificateDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .in(HrmEmployeeCertificateDO::getId, ids));
    }

}
