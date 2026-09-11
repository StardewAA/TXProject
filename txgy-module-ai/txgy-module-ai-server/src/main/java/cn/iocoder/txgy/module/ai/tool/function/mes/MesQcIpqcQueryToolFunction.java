package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import cn.iocoder.txgy.module.mes.api.qc.MesQcIpqcApi;
import cn.iocoder.txgy.module.mes.api.qc.dto.MesQcIpqcRespDTO;
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
 * 工具：MES 过程检验单（IPQC）查询（只读）
 *
 * @author OPENLAB BS
 */
@Component("mes_qc_ipqc_query")
public class MesQcIpqcQueryToolFunction
        implements BiFunction<MesQcIpqcQueryToolFunction.Request, ToolContext, MesQcIpqcQueryToolFunction.Response> {

    @Resource
    private MesQcIpqcApi ipqcApi;

    @Data
    @JsonClassDescription("MES 过程检验单查询：支持按检验单编号、所属工单、检测结果、状态查询，可分页")
    public static class Request {

        @JsonProperty(value = "id")
        @JsonPropertyDescription("检验单编号（数据库主键），精确查询单个检验单时使用")
        private Long id;

        @JsonProperty(value = "code")
        @JsonPropertyDescription("检验单编号字符串，例如：IPQC20250101001，模糊匹配")
        private String code;

        @JsonProperty(value = "workOrderId")
        @JsonPropertyDescription("所属生产工单编号")
        private Long workOrderId;

        @JsonProperty(value = "checkResult")
        @JsonPropertyDescription("检测结果：1=合格，2=不合格（具体以系统字典为准）")
        private Integer checkResult;

        @JsonProperty(value = "status")
        @JsonPropertyDescription("检验单状态（具体以系统字典为准）")
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
         * 检验单列表
         */
        private List<MesQcIpqcRespDTO> list;

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
                MesQcIpqcRespDTO ipqc = ipqcApi.getIpqc(request.getId()).getCheckedData();
                return new Response(true, null, ipqc == null ? 0L : 1L,
                        ipqc == null ? List.of() : List.of(ipqc));
            }
            // 分页查询
            PageResult<MesQcIpqcRespDTO> pageResult = ipqcApi.getIpqcPage(
                    request.getCode(), request.getWorkOrderId(), null, request.getCheckResult(), request.getStatus(),
                    request.getPageNo() == null ? 1 : request.getPageNo(),
                    request.getPageSize() == null ? 10 : Math.min(request.getPageSize(), 20)).getCheckedData();
            return new Response(true, null, pageResult.getTotal(), pageResult.getList());
        });
    }

}
