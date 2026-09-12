package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import cn.iocoder.txgy.module.mes.api.pro.MesProCardApi;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProCardRespDTO;
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
 * 工具：MES 生产流转卡查询（只读）
 *
 * @author OPENLAB BS
 */
@Component("mes_card_query")
public class MesCardQueryToolFunction
        implements BiFunction<MesCardQueryToolFunction.Request, ToolContext, MesCardQueryToolFunction.Response> {

    @Resource
    private MesProCardApi cardApi;

    @Data
    @JsonClassDescription("MES 生产流转卡查询：按流转卡编号、编码、批次号查询生产流转卡信息，可分页")
    public static class Request {

        @JsonProperty(value = "id")
        @JsonPropertyDescription("流转卡编号（数据库主键），精确查询单张流转卡时使用")
        private Long id;

        @JsonProperty(value = "code")
        @JsonPropertyDescription("流转卡编码，例如：CARD-001，模糊匹配")
        private String code;

        @JsonProperty(value = "batchCode")
        @JsonPropertyDescription("批次号，例如：BATCH-001，精确匹配")
        private String batchCode;

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
         * 生产流转卡列表
         */
        private List<MesProCardRespDTO> list;

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
                MesProCardRespDTO card = cardApi.getCard(request.getId()).getCheckedData();
                return new Response(true, null, card == null ? 0L : 1L,
                        card == null ? List.of() : List.of(card));
            }
            // 分页查询
            PageResult<MesProCardRespDTO> pageResult = cardApi.getCardPage(
                    request.getCode(), null, null, request.getBatchCode(),
                    request.getPageNo() == null ? 1 : request.getPageNo(),
                    request.getPageSize() == null ? 10 : Math.min(request.getPageSize(), 20)).getCheckedData();
            return new Response(true, null, pageResult.getTotal(), pageResult.getList());
        });
    }

}
