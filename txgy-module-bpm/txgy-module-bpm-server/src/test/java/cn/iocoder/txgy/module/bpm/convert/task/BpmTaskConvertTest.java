package cn.iocoder.txgy.module.bpm.convert.task;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.impl.persistence.entity.HistoricTaskInstanceEntityImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BpmTaskConvertTest {

    @Test
    void buildTodoTaskPage_returnsBusinessOrderNumber() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setProcessInstanceId("process-1");
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getProcessVariables()).thenReturn(Map.of("orderNo", "RK-20260920-001"));

        PageResult<BpmTaskRespVO> result = BpmTaskConvert.INSTANCE.buildTodoTaskPage(
                new PageResult<>(List.of(task), 1L), Map.of("process-1", processInstance), emptyMap(), emptyMap());

        assertEquals("RK-20260920-001", result.getList().get(0).getProcessInstance().getOrderNo());
    }

    @Test
    void buildTaskPage_returnsBusinessOrderNumber() {
        HistoricTaskInstanceEntityImpl task = new HistoricTaskInstanceEntityImpl();
        task.setProcessInstanceId("process-1");
        HistoricProcessInstance processInstance = mock(HistoricProcessInstance.class);
        when(processInstance.getProcessVariables()).thenReturn(Map.of("orderNo", "CK-20260920-001"));

        PageResult<BpmTaskRespVO> result = BpmTaskConvert.INSTANCE.buildTaskPage(
                new PageResult<>(List.of(task), 1L), Map.of("process-1", processInstance), emptyMap(), emptyMap(), emptyMap(), null);

        assertEquals("CK-20260920-001", result.getList().get(0).getProcessInstance().getOrderNo());
    }
}
