package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import cn.iocoder.txgy.module.mes.api.pro.MesProAndonRecordApi;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProAndonRecordRespDTO;
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
 * 工具：MES 安灯呼叫记录查询（只读）
 *
 * @author OPENLAB BS
 */
@Component("mes_andon_query")
public class MesAndonQueryToolFunction
        implements BiFunction<MesAndonQueryToolFunction.Request, ToolContext, MesAndonQueryToolFunction.Response> {

    @Resource
    private MesProAndonRecordApi andonRecordApi;

    @Data
    @JsonClassDescription("MES 安灯呼叫查询：按工作站编码、处置状态、呼叫时间范围查询安灯呼叫及处置情况，可分页")
    public static class Request {

        @JsonProperty(value = "id")
        @JsonPropertyDescription("安灯呼叫记录编号（数据库主键），精确查询单条时使用")
        private Long id;

        @JsonProperty(value = "workstationCode")
        @JsonPropertyDescription("工作站编码，例如：ST001-2010，精确匹配")
        private String workstationCode;

        @JsonProperty(value = "status")
        @JsonPropertyDescription("处置状态：0=未处理，1=已处理（具体以系统字典为准）")
        private Integer status;

        @JsonProperty(value = "createTimeBegin")
        @JsonPropertyDescription("呼叫时间范围-开始，格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd")
        private String createTimeBegin;

        @JsonProperty(value = "createTimeEnd")
        @JsonPropertyDescription("呼叫时间范围-结束，格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd")
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
         * 安灯呼叫记录列表
         */
        private List<MesProAndonRecordRespDTO> list;

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
                MesProAndonRecordRespDTO andonRecord = andonRecordApi.getAndonRecord(request.getId()).getCheckedData();
                return new Response(true, null, andonRecord == null ? 0L : 1L,
                        andonRecord == null ? List.of() : List.of(andonRecord));
            }
            // 分页查询（支持工作站编码精确匹配）
            PageResult<MesProAndonRecordRespDTO> pageResult = andonRecordApi.getAndonRecordPage(
                    null, request.getWorkstationCode(), null, null, request.getStatus(),
                    request.getCreateTimeBegin(), request.getCreateTimeEnd(),
                    request.getPageNo() == null ? 1 : request.getPageNo(),
                    request.getPageSize() == null ? 10 : Math.min(request.getPageSize(), 20)).getCheckedData();
            return new Response(true, null, pageResult.getTotal(), pageResult.getList());
        });
    }

}
