package cn.iocoder.txgy.module.bpm.framework.flowable.core.candidate.strategy.user;

import cn.iocoder.txgy.framework.common.util.string.StrUtils;
import cn.iocoder.txgy.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.txgy.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.txgy.module.system.api.permission.PermissionApi;
import cn.iocoder.txgy.module.system.api.permission.RoleApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;

import java.util.Map;

import java.util.Set;

/**
 * 角色 {@link BpmTaskCandidateStrategy} 实现类
 *
 * @author kyle
 */
@Component
public class BpmTaskCandidateRoleStrategy implements BpmTaskCandidateStrategy {

    @Resource
    private RoleApi roleApi;
    @Resource
    private PermissionApi permissionApi;

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.ROLE;
    }

    @Override
    public void validateParam(String param) {
        Set<Long> roleIds = StrUtils.splitToLongSet(param);
        roleApi.validRoleList(roleIds).checkError();
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        Set<Long> roleIds = StrUtils.splitToLongSet(param);
        return permissionApi.getUserRoleIdListByRoleIds(roleIds).getCheckedData();
    }

    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        Long managerUserId = toLong(execution.getVariable("wmsWarehouseManagerUserId"));
        return managerUserId == null ? calculateUsers(param) : Set.of(managerUserId);
    }

    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                              Long startUserId, String processDefinitionId,
                                              Map<String, Object> processVariables) {
        Long managerUserId = processVariables == null ? null : toLong(processVariables.get("wmsWarehouseManagerUserId"));
        return managerUserId == null ? calculateUsers(param) : Set.of(managerUserId);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string && !string.isBlank()) return Long.valueOf(string);
        return null;
    }

}
