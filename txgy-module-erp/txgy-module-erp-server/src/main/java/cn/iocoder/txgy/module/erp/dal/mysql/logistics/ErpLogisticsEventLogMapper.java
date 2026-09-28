package cn.iocoder.txgy.module.erp.dal.mysql.logistics;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsEventLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpLogisticsEventLogMapper extends BaseMapperX<ErpLogisticsEventLogDO> {

    default ErpLogisticsEventLogDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(ErpLogisticsEventLogDO::getId, id);
    }

    default PageResult<ErpLogisticsEventLogDO> selectPage(ErpLogisticsEventPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpLogisticsEventLogDO>()
                .likeIfPresent(ErpLogisticsEventLogDO::getLogisticsTaskNo, reqVO.getLogisticsTaskNo())
                .eqIfPresent(ErpLogisticsEventLogDO::getEventType, reqVO.getEventType())
                .eqIfPresent(ErpLogisticsEventLogDO::getProcessStatus, reqVO.getProcessStatus())
                .orderByDesc(ErpLogisticsEventLogDO::getId));
    }

    default ErpLogisticsEventLogDO selectByRequestId(String requestId) {
        return selectOne(ErpLogisticsEventLogDO::getRequestId, requestId);
    }

    default ErpLogisticsEventLogDO selectByRequestIdForUpdate(String requestId) {
        return selectOneForUpdate(ErpLogisticsEventLogDO::getRequestId, requestId);
    }

    default ErpLogisticsEventLogDO selectByTaskAndVersion(String logisticsTaskNo, Long eventVersion) {
        return selectOne(new LambdaQueryWrapperX<ErpLogisticsEventLogDO>()
                .eq(ErpLogisticsEventLogDO::getLogisticsTaskNo, logisticsTaskNo)
                .eq(ErpLogisticsEventLogDO::getEventVersion, eventVersion));
    }

    default ErpLogisticsEventLogDO selectByTaskAndVersionForUpdate(String logisticsTaskNo, Long eventVersion) {
        return selectOneForUpdate(new LambdaQueryWrapperX<ErpLogisticsEventLogDO>()
                .eq(ErpLogisticsEventLogDO::getLogisticsTaskNo, logisticsTaskNo)
                .eq(ErpLogisticsEventLogDO::getEventVersion, eventVersion));
    }

}
