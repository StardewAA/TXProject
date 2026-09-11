package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import cn.iocoder.txgy.module.mes.api.pro.MesProWorkRecordApi;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProWorkRecordLogRespDTO;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProWorkRecordRespDTO;
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
 * 工具：MES 生产记录（上下工）查询（只读）
 *
 * @author OPENLAB BS
 */
@Component("mes_workrecord_query")
public class MesWorkRecordQueryToolFunction
        implements BiFunction<MesWorkRecordQueryToolFunction.Request, ToolContext, MesWorkRecordQueryToolFunction.Response> {

    @Resource
    private MesProWorkRecordApi workRecordApi;

    @Data
    @JsonClassDescription("MES 生产记录查询：查询人员当前上工状态、上下工记录流水，支持按用户、工作站编码、时间范围过滤")
    public static class Request {

        @JsonProperty(value = "id")
        @JsonPropertyDescription("上下工记录流水编号（数据库主键），精确查询单条流水时使用")
        private Long id;

        @JsonProperty(value = "current")
        @JsonPropertyDescription("是否查询用户当前的上工状态（true 表示按 userId 查当前绑定，默认 false）")
        private Boolean current;

        @JsonProperty(value = "userId")
        @JsonPropertyDescription("用户编号")
        private Long userId;

        @JsonProperty(value = "workstationCode")
        @JsonPropertyDescription("工作站编码，例如：ST001-2010，精确匹配")
        private String workstationCode;

        @JsonProperty(value = "type")
        @JsonPropertyDescription("操作类型：1=上工，2=下工")
        private Integer type;

        @JsonProperty(value = "createTimeBegin")
        @JsonPropertyDescription("时间范围-开始，格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd")
        private String createTimeBegin;

        @JsonProperty(value = "createTimeEnd")
        @JsonPropertyDescription("时间范围-结束，格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd")
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
         * 总条数（上下工流水的总条数）
         */
        private Long total;
        /**
         * 当前上工状态（按 userId 查询时返回）
         */
        private MesProWorkRecordRespDTO current;
        /**
         * 上下工记录流水列表
         */
        private List<MesProWorkRecordLogRespDTO> list;

    }

    @Override
    public Response apply(Request request, ToolContext toolContext) {
        Long tenantId = (Long) toolContext.getContext().get(AiUtils.TOOL_CONTEXT_TENANT_ID);
        if (tenantId == null) {
            return new Response(false, "无法识别当前租户", 0L, null, List.of());
        }
        return TenantUtils.execute(tenantId, () -> {
            // 查询用户当前上工状态
            if (Boolean.TRUE.equals(request.getCurrent())) {
                if (request.getUserId() == null) {
                    return new Response(false, "查询当前上工状态必须提供 userId", 0L, null, List.of());
                }
                MesProWorkRecordRespDTO current = workRecordApi.getWorkRecordByUserId(request.getUserId()).getCheckedData();
                return new Response(true, null, current == null ? 0L : 1L, current,
                        current == null ? List.of() : List.of());
            }
            // 按流水主键精确查询
            if (request.getId() != null) {
                MesProWorkRecordLogRespDTO log = workRecordApi.getWorkRecordLog(request.getId()).getCheckedData();
                return new Response(true, null, log == null ? 0L : 1L, null,
                        log == null ? List.of() : List.of(log));
            }
            // 分页查询流水
            PageResult<MesProWorkRecordLogRespDTO> pageResult = workRecordApi.getWorkRecordLogPage(
                    request.getUserId(), null, request.getWorkstationCode(), request.getType(),
                    request.getCreateTimeBegin(), request.getCreateTimeEnd(),
                    request.getPageNo() == null ? 1 : request.getPageNo(),
                    request.getPageSize() == null ? 10 : Math.min(request.getPageSize(), 20)).getCheckedData();
            return new Response(true, null, pageResult.getTotal(), null, pageResult.getList());
        });
    }

}
