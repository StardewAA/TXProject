package cn.iocoder.txgy.module.hrm.dal.mysql.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateWarningDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmCertificateWarningMapper extends BaseMapperX<HrmCertificateWarningDO> {

    default PageResult<HrmCertificateWarningDO> selectPage(HrmCertificateWarningPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmCertificateWarningDO>()
                .eqIfPresent(HrmCertificateWarningDO::getCertificateId, reqVO.getCertificateId())
                .eqIfPresent(HrmCertificateWarningDO::getEmployeeId, reqVO.getEmployeeId())
                .eqIfPresent(HrmCertificateWarningDO::getType, reqVO.getType())
                .eqIfPresent(HrmCertificateWarningDO::getStatus, reqVO.getStatus())
                .eqIfPresent(HrmCertificateWarningDO::getNotifyStatus, reqVO.getNotifyStatus())
                .betweenIfPresent(HrmCertificateWarningDO::getWarningDate,
                        reqVO.getWarningDateStart(), reqVO.getWarningDateEnd())
                .orderByDesc(HrmCertificateWarningDO::getWarningDate)
                .orderByDesc(HrmCertificateWarningDO::getId));
    }

    /**
     * 按「证件 + 预警类型 + 预警日期」查询，用于每日扫描的幂等去重
     */
    default HrmCertificateWarningDO selectByUniqueKey(Long certificateId, Integer type, LocalDate warningDate) {
        return selectOne(new LambdaQueryWrapperX<HrmCertificateWarningDO>()
                .eq(HrmCertificateWarningDO::getCertificateId, certificateId)
                .eq(HrmCertificateWarningDO::getType, type)
                .eq(HrmCertificateWarningDO::getWarningDate, warningDate)
                .orderByAsc(HrmCertificateWarningDO::getId)
                .last("LIMIT 1"));
    }

    default List<HrmCertificateWarningDO> selectListByCertificateId(Long certificateId) {
        return selectList(new LambdaQueryWrapperX<HrmCertificateWarningDO>()
                .eq(HrmCertificateWarningDO::getCertificateId, certificateId)
                .orderByDesc(HrmCertificateWarningDO::getId));
    }

    default Long selectCountByStatus(Integer status) {
        return selectCount(new LambdaQueryWrapperX<HrmCertificateWarningDO>()
                .eq(HrmCertificateWarningDO::getStatus, status));
    }

    default List<HrmCertificateWarningDO> selectListByNotifyStatus(Integer notifyStatus) {
        return selectList(new LambdaQueryWrapperX<HrmCertificateWarningDO>()
                .eq(HrmCertificateWarningDO::getNotifyStatus, notifyStatus)
                .orderByAsc(HrmCertificateWarningDO::getId));
    }

    default List<HrmCertificateWarningDO> selectListByCertificateIds(Collection<Long> certificateIds) {
        return selectList(new LambdaQueryWrapperX<HrmCertificateWarningDO>()
                .in(HrmCertificateWarningDO::getCertificateId, certificateIds)
                .orderByDesc(HrmCertificateWarningDO::getId));
    }

    default List<HrmCertificateWarningDO> selectListByCertificateIdsAndStatus(Collection<Long> certificateIds,
                                                                             Integer status) {
        return selectList(new LambdaQueryWrapperX<HrmCertificateWarningDO>()
                .in(HrmCertificateWarningDO::getCertificateId, certificateIds)
                .eqIfPresent(HrmCertificateWarningDO::getStatus, status));
    }

    /**
     * 更新处理信息，重新启用时需要把处理人、处理时间与处理意见一并清空，因此使用显式 set
     */
    default int updateHandleInfo(Long id, Integer status, Long handleUserId, LocalDateTime handleTime,
                                 String handleRemark) {
        return update(null, new LambdaUpdateWrapper<HrmCertificateWarningDO>()
                .eq(HrmCertificateWarningDO::getId, id)
                .set(HrmCertificateWarningDO::getStatus, status)
                .set(HrmCertificateWarningDO::getHandleUserId, handleUserId)
                .set(HrmCertificateWarningDO::getHandleTime, handleTime)
                .set(HrmCertificateWarningDO::getHandleRemark, handleRemark));
    }

    /**
     * 更新通知结果，失败原因在重试成功时同样需要清空
     */
    default int updateNotifyInfo(Long id, Integer notifyStatus, LocalDateTime notifyTime, String notifyFailReason,
                                 Integer notifyRetryCount) {
        return update(null, new LambdaUpdateWrapper<HrmCertificateWarningDO>()
                .eq(HrmCertificateWarningDO::getId, id)
                .set(HrmCertificateWarningDO::getNotifyStatus, notifyStatus)
                .set(HrmCertificateWarningDO::getNotifyTime, notifyTime)
                .set(HrmCertificateWarningDO::getNotifyFailReason, notifyFailReason)
                .set(HrmCertificateWarningDO::getNotifyRetryCount, notifyRetryCount));
    }

}
