package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.OperationLogPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.OperationLogDO;

/**
 * 资源中心操作日志 Service
 *
 * @author 云培训学院课题小组
 */
public interface OperationLogService {

    /**
     * 记录一条操作日志（操作人取自当前登录态）
     *
     * @param action     动作（ResourceConstants.ACTION_*）
     * @param targetType 对象类型（document/video/question/category/tag）
     * @param targetId   对象 ID
     * @param detail     变更摘要
     */
    void createLog(String action, String targetType, Long targetId, String detail);

    /**
     * 分页查询操作日志
     */
    PageResult<OperationLogDO> getLogPage(OperationLogPageReqVO reqVO);

}
