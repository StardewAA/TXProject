package cn.iocoder.txgy.module.training.dal.mysql.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.OperationLogPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.OperationLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源中心操作日志 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface OperationLogMapper extends BaseMapperX<OperationLogDO> {

    default PageResult<OperationLogDO> selectPage(OperationLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<OperationLogDO>()
                .likeIfPresent(OperationLogDO::getUserName, reqVO.getKeyword())
                .eqIfPresent(OperationLogDO::getAction, reqVO.getAction())
                .eqIfPresent(OperationLogDO::getTargetType, reqVO.getTargetType())
                .betweenIfPresent(OperationLogDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(OperationLogDO::getId));
    }

}
