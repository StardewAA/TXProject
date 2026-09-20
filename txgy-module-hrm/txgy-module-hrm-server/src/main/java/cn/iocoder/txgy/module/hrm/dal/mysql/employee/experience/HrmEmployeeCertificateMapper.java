package cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateLedgerPageReqVO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Objects;

@Mapper
public interface HrmEmployeeCertificateMapper extends BaseMapperX<HrmEmployeeCertificateDO> {

    default List<HrmEmployeeCertificateDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .eq(HrmEmployeeCertificateDO::getEmployeeId, employeeId)
                .orderByAsc(HrmEmployeeCertificateDO::getSort)
                .orderByDesc(HrmEmployeeCertificateDO::getId));
    }

    /**
     * 某证照类型下的持证条数（证照类型列表展示用）
     */
    default Long selectCountByTypeId(Long typeId) {
        return selectCount(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .eq(HrmEmployeeCertificateDO::getTypeId, typeId));
    }

    /**
     * 批量查询多个员工的证照（持证率统计用）
     */
    default List<HrmEmployeeCertificateDO> selectListByEmployeeIds(java.util.Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return selectList(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .in(HrmEmployeeCertificateDO::getEmployeeId, employeeIds));
    }

    /**
     * 按到期时间区间查询（证照台账 / 到期预警用）
     */
    default List<HrmEmployeeCertificateDO> selectListByEndTimeBetween(java.time.LocalDateTime start,
                                                                     java.time.LocalDateTime end) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .ge(HrmEmployeeCertificateDO::getEndTime, start)
                .le(HrmEmployeeCertificateDO::getEndTime, end)
                .orderByAsc(HrmEmployeeCertificateDO::getEndTime));
    }

    /**
     * 查询已过期证照
     */
    default List<HrmEmployeeCertificateDO> selectExpiredList(java.time.LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .lt(HrmEmployeeCertificateDO::getEndTime, now)
                .orderByDesc(HrmEmployeeCertificateDO::getEndTime));
    }

    /**
     * 证照台账分页：支持按类型 / 员工 / 名称 / 核验状态 / 到期状态筛选
     */
    default PageResult<HrmEmployeeCertificateDO> selectLedgerPage(HrmCertificateLedgerPageReqVO reqVO) {
        return selectPage(reqVO, buildLedgerWrapper(reqVO));
    }

    /**
     * 证照台账列表（导出用）
     */
    default List<HrmEmployeeCertificateDO> selectLedgerList(HrmCertificateLedgerPageReqVO reqVO) {
        return selectList(buildLedgerWrapper(reqVO));
    }

    /**
     * 到期预警清单：临期（按未来 N 天）+ 已过期
     */
    default List<HrmEmployeeCertificateDO> selectExpireWarningList(int warnDays) {
        LocalDateTime now = LocalDateTime.now();
        return selectList(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .isNotNull(HrmEmployeeCertificateDO::getEndTime)
                .le(HrmEmployeeCertificateDO::getEndTime, now.plusDays(warnDays))
                .orderByAsc(HrmEmployeeCertificateDO::getEndTime));
    }

    private LambdaQueryWrapperX<HrmEmployeeCertificateDO> buildLedgerWrapper(
            HrmCertificateLedgerPageReqVO reqVO) {
        LocalDateTime now = LocalDateTime.now();
        int warnDays = reqVO.getWarnDays() == null ? 90 : reqVO.getWarnDays();
        LambdaQueryWrapperX<HrmEmployeeCertificateDO> wrapper = new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                .eqIfPresent(HrmEmployeeCertificateDO::getTypeId, reqVO.getTypeId())
                .eqIfPresent(HrmEmployeeCertificateDO::getEmployeeId, reqVO.getEmployeeId())
                .likeIfPresent(HrmEmployeeCertificateDO::getName, reqVO.getCertificateName())
                .eqIfPresent(HrmEmployeeCertificateDO::getVerifyStatus, reqVO.getVerifyStatus());
        // 到期状态：1 有效（warnDays 之后到期）、2 临期（warnDays 内到期）、3 已过期
        if (Objects.equals(reqVO.getExpireStatus(), 3)) {
            wrapper.lt(HrmEmployeeCertificateDO::getEndTime, now);
        } else if (Objects.equals(reqVO.getExpireStatus(), 2)) {
            wrapper.ge(HrmEmployeeCertificateDO::getEndTime, now)
                    .le(HrmEmployeeCertificateDO::getEndTime, now.plusDays(warnDays));
        } else if (Objects.equals(reqVO.getExpireStatus(), 1)) {
            wrapper.gt(HrmEmployeeCertificateDO::getEndTime, now.plusDays(warnDays));
        }
        // 注意：orderByAsc 返回的是 MyBatis-Plus 的父类型，这里单独调用以保留 LambdaQueryWrapperX 类型
        wrapper.orderByAsc(HrmEmployeeCertificateDO::getEndTime);
        return wrapper;
    }

}
