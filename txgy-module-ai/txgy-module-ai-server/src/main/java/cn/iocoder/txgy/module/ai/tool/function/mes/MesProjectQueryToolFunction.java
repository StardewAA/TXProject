package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import cn.iocoder.txgy.module.mes.api.pro.MesProProjectApi;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProProjectRespDTO;
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
 * 工具：MES 项目查询（只读）
 *
 * @author OPENLAB BS
 */
@Component("mes_project_query")
public class MesProjectQueryToolFunction
        implements BiFunction<MesProjectQueryToolFunction.Request, ToolContext, MesProjectQueryToolFunction.Response> {

    @Resource
    private MesProProjectApi projectApi;

    @Data
    @JsonClassDescription("MES 项目查询：按项目编号、编码、名称、状态、来源类型、优先级、负责人查询，可分页")
    public static class Request {

        @JsonProperty(value = "id")
        @JsonPropertyDescription("项目编号（数据库主键），精确查询单个项目时使用")
        private Long id;

        @JsonProperty(value = "code")
        @JsonPropertyDescription("项目编码，例如：PRJ20260902001，模糊匹配")
        private String code;

        @JsonProperty(value = "name")
        @JsonPropertyDescription("项目名称，模糊匹配")
        private String name;

        @JsonProperty(value = "status")
        @JsonPropertyDescription("项目状态（具体以系统字典为准）")
        private Integer status;

        @JsonProperty(value = "sourceType")
        @JsonPropertyDescription("项目来源类型：1 销售订单、2 库存备库、3 其他")
        private Integer sourceType;

        @JsonProperty(value = "priority")
        @JsonPropertyDescription("项目优先级：1 紧急、2 高、3 中、4 低")
        private Integer priority;

        @JsonProperty(value = "owner")
        @JsonPropertyDescription("负责人姓名，精确匹配")
        private String owner;

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
         * 项目列表
         */
        private List<MesProProjectRespDTO> list;

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
                MesProProjectRespDTO project = projectApi.getProject(request.getId()).getCheckedData();
                return new Response(true, null, project == null ? 0L : 1L,
                        project == null ? List.of() : List.of(project));
            }
            // 分页查询
            PageResult<MesProProjectRespDTO> pageResult = projectApi.getProjectPage(
                    request.getCode(), request.getName(), request.getStatus(), request.getSourceType(),
                    request.getPriority(), request.getOwner(), null,
                    request.getPageNo() == null ? 1 : request.getPageNo(),
                    request.getPageSize() == null ? 10 : Math.min(request.getPageSize(), 20)).getCheckedData();
            return new Response(true, null, pageResult.getTotal(), pageResult.getList());
        });
    }

}
