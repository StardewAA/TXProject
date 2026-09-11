package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import cn.iocoder.txgy.module.mes.api.pro.MesProRouteApi;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProRouteRespDTO;
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
 * 工具：MES 工艺路线查询（只读）
 *
 * @author OPENLAB BS
 */
@Component("mes_route_query")
public class MesRouteQueryToolFunction
        implements BiFunction<MesRouteQueryToolFunction.Request, ToolContext, MesRouteQueryToolFunction.Response> {

    @Resource
    private MesProRouteApi routeApi;

    @Data
    @JsonClassDescription("MES 工艺路线查询：按工艺路线编号、编码、名称、状态查询工艺路线及说明，可分页")
    public static class Request {

        @JsonProperty(value = "id")
        @JsonPropertyDescription("工艺路线编号（数据库主键），精确查询单条工艺路线时使用")
        private Long id;

        @JsonProperty(value = "code")
        @JsonPropertyDescription("工艺路线编码，例如：ROUTE001，模糊匹配")
        private String code;

        @JsonProperty(value = "name")
        @JsonPropertyDescription("工艺路线名称，例如：标准路线，模糊匹配")
        private String name;

        @JsonProperty(value = "status")
        @JsonPropertyDescription("状态：0=启用，1=停用（具体以系统字典为准）")
        private Integer status;

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
         * 工艺路线列表
         */
        private List<MesProRouteRespDTO> list;

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
                MesProRouteRespDTO route = routeApi.getRoute(request.getId()).getCheckedData();
                return new Response(true, null, route == null ? 0L : 1L,
                        route == null ? List.of() : List.of(route));
            }
            // 分页查询
            PageResult<MesProRouteRespDTO> pageResult = routeApi.getRoutePage(
                    request.getCode(), request.getName(), request.getStatus(),
                    request.getPageNo() == null ? 1 : request.getPageNo(),
                    request.getPageSize() == null ? 10 : Math.min(request.getPageSize(), 20)).getCheckedData();
            return new Response(true, null, pageResult.getTotal(), pageResult.getList());
        });
    }

}
