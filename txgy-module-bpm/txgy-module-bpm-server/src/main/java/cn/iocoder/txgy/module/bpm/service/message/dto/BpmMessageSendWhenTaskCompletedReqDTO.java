package cn.iocoder.txgy.module.bpm.service.message.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** BPM 发送审批操作完成 Request DTO。 */
@Data
public class BpmMessageSendWhenTaskCompletedReqDTO {

    @NotEmpty(message = "流程实例的编号不能为空")
    private String processInstanceId;
    @NotEmpty(message = "流程定义标识不能为空")
    private String processDefinitionKey;
    @NotEmpty(message = "流程任务的名字不能为空")
    private String taskName;
    @NotNull(message = "审批人的用户编号不能为空")
    private Long assigneeUserId;
    @NotEmpty(message = "审批结果不能为空")
    private String result;

    private String orderType;
    private String orderNo;
}
