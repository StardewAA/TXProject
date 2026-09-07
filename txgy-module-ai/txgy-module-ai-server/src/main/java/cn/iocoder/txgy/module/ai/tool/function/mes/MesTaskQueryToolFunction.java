package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import cn.iocoder.txgy.module.mes.api.pro.MesProTaskApi;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProTaskRespDTO;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 工具：MES 生产任务查询（只读）
 *
 * @author OPENLAB BS
 */
@Component("mes_task_query")
public class MesTaskQueryToolFunction
        implements BiFunction<MesTaskQueryToolFunction.Request, ToolContext, MesTaskQueryToolFunction.Response> {

    @Resource
    private MesProTaskApi taskApi;

    @Data
    @JsonClassDescription("MES 生产任务查询：支持按任务编号、任务编码、所属工单、状态、创建时间范围查询，可分页")
    public static class Request {

        @JsonProperty(value = "id")
        @JsonPropertyDescription("任务编号（数据库主键），精确查询单个任务时使用")
        private Long id;

        @JsonProperty(value = "code")
        @JsonPropertyDescription("任务编码，例如：PT202503150001，模糊匹配")
        private String code;

        @JsonProperty(value = "name")
        @JsonPropertyDescription("任务名称，模糊匹配")
        private String name;

        @JsonProperty(value = "workOrderId")
        @JsonPropertyDescription("所属生产工单编号")
        private Long workOrderId;

        @JsonProperty(value = "status")
        @JsonPropertyDescription("任务状态：0=待生产，1=生产中，2=已完成，-1=已取消（具体以系统字典为准）")
        private Integer status;

        @JsonProperty(value = "createTimeBegin")
        @JsonPropertyDescription("创建时间范围-开始，格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd")
        private String createTimeBegin;

        @JsonProperty(value = "createTimeEnd")
        @JsonPropertyDescription("创建时间范围-结束，格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd")
        private String createTimeEnd;

        @JsonProperty(value = "pageNo")
        @JsonPropertyDescription("页码，从 1 开始，默认 1")
        private Integer pageNo;

        @JsonProperty(value = "pageSize")
        @JsonPropertyDescription("每页条数，默认 10，最大 20")
        private Integer pageSize;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Response {

        /**
         * 是否查询成功
         */
        private Boolean success;
        /**
         * 提示信息（失败原因等）
         */
        private String message;
        /**
         * 总条数
         */
        private Long total;
        /**
         * 任务列表
         */
        private List<MesProTaskRespDTO> list;

    }

    @Override
    public Response apply(Request request, ToolContext toolContext) {
        Long tenantId = (Long) toolContext.getContext().get(AiUtils.TOOL_CONTEXT_TENANT_ID);
        if (tenantId == null) {
            return new Response(false, "无法识别当前租户", 0L, List.of());
        }
        return TenantUtils.execute(tenantId, () -> {
            // 按主键精确查询
            if (request.getId() != null) {
                MesProTaskRespDTO task = taskApi.getTask(request.getId()).getCheckedData();
                return new Response(true, null, task == null ? 0L : 1L,
                        task == null ? List.of() : List.of(task));
            }
            // 分页查询
            PageResult<MesProTaskRespDTO> pageResult = taskApi.getTaskPage(
                    request.getCode(), request.getName(), request.getWorkOrderId(), request.getStatus(),
                    request.getCreateTimeBegin(), request.getCreateTimeEnd(),
                    request.getPageNo() == null ? 1 : request.getPageNo(),
                    request.getPageSize() == null ? 10 : Math.min(request.getPageSize(), 20)).getCheckedData();
            return new Response(true, null, pageResult.getTotal(), pageResult.getList());
        });
    }

}
