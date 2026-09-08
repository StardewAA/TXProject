package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import cn.iocoder.txgy.module.mes.api.pro.MesProFeedbackApi;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProFeedbackRespDTO;
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
 * 工具：MES 生产报工记录查询（只读）
 *
 * @author OPENLAB BS
 */
@Component("mes_feedback_query")
public class MesFeedbackQueryToolFunction
        implements BiFunction<MesFeedbackQueryToolFunction.Request, ToolContext, MesFeedbackQueryToolFunction.Response> {

    @Resource
    private MesProFeedbackApi feedbackApi;

    @Data
    @JsonClassDescription("MES 生产报工记录查询：支持按报工单编号、所属工单、状态、报工时间范围查询，可分页")
    public static class Request {

        @JsonProperty(value = "id")
        @JsonPropertyDescription("报工单编号（数据库主键），精确查询单条报工时使用")
        private Long id;

        @JsonProperty(value = "code")
        @JsonPropertyDescription("报工单编号字符串，例如：FB202503160001，模糊匹配")
        private String code;

        @JsonProperty(value = "workOrderId")
        @JsonPropertyDescription("所属生产工单编号")
        private Long workOrderId;

        @JsonProperty(value = "status")
        @JsonPropertyDescription("报工状态：0=待提交，1=待审核，2=已审核，-1=已驳回（具体以系统字典为准）")
        private Integer status;

        @JsonProperty(value = "feedbackTimeBegin")
        @JsonPropertyDescription("报工时间范围-开始，格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd")
        private String feedbackTimeBegin;

        @JsonProperty(value = "feedbackTimeEnd")
        @JsonPropertyDescription("报工时间范围-结束，格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd")
        private String feedbackTimeEnd;

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
         * 报工记录列表
         */
        private List<MesProFeedbackRespDTO> list;

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
                MesProFeedbackRespDTO feedback = feedbackApi.getFeedback(request.getId()).getCheckedData();
                return new Response(true, null, feedback == null ? 0L : 1L,
                        feedback == null ? List.of() : List.of(feedback));
            }
            // 分页查询
            PageResult<MesProFeedbackRespDTO> pageResult = feedbackApi.getFeedbackPage(
                    request.getCode(), request.getWorkOrderId(), request.getStatus(), null,
                    request.getFeedbackTimeBegin(), request.getFeedbackTimeEnd(),
                    request.getPageNo() == null ? 1 : request.getPageNo(),
                    request.getPageSize() == null ? 10 : Math.min(request.getPageSize(), 20)).getCheckedData();
            return new Response(true, null, pageResult.getTotal(), pageResult.getList());
        });
    }

}
