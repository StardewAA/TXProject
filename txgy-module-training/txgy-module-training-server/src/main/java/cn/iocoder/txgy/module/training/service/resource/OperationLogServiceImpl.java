package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.OperationLogPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.OperationLogDO;
import cn.iocoder.txgy.module.training.dal.mysql.resource.OperationLogMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资源中心操作日志 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Resource
    private OperationLogMapper operationLogMapper;

    @Override
    public void createLog(String action, String targetType, Long targetId, String detail) {
        try {
            OperationLogDO logDO = new OperationLogDO();
            logDO.setAction(action);
            logDO.setTargetType(targetType);
            logDO.setTargetId(targetId);
            logDO.setDetail(detail);
            logDO.setUserId(SecurityFrameworkUtils.getLoginUserId());
            logDO.setUserName(SecurityFrameworkUtils.getLoginUserNickname());
            operationLogMapper.insert(logDO);
        } catch (Exception e) {
            // 日志失败不影响主流程（如异步/无登录态预览直出场景）
            log.warn("[createLog][记录操作日志失败 action({}) targetType({}) targetId({})]",
                    action, targetType, targetId, e);
        }
    }

    @Override
    public PageResult<OperationLogDO> getLogPage(OperationLogPageReqVO reqVO) {
        return operationLogMapper.selectPage(reqVO);
    }

}
