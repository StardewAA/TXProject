package cn.iocoder.txgy.module.bpm.service.task;

import cn.iocoder.txgy.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.txgy.module.bpm.controller.admin.task.vo.task.BpmTaskPageReqVO;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BpmTaskServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmTaskServiceImpl taskServiceImpl;

    @Mock
    private TaskService taskService;

    @Mock(answer = Answers.RETURNS_SELF)
    private TaskQuery taskQuery;

    @Test
    void getTaskTodoPage_filtersByBusinessOrderNumber() {
        BpmTaskPageReqVO pageReqVO = new BpmTaskPageReqVO();
        ReflectionTestUtils.setField(pageReqVO, "orderNo", "RK-20260920-001");
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(0L);

        taskServiceImpl.getTaskTodoPage(1L, pageReqVO);

        verify(taskQuery).processVariableValueLike("orderNo", "%RK-20260920-001%");
    }
}
