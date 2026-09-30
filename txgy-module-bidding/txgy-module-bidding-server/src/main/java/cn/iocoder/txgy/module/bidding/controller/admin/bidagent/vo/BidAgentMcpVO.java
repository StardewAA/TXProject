package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** MCP 服务 VO（外部 MCP server 登记、自检、试调） */
public class BidAgentMcpVO {

    @Schema(description = "分页入参")
    @Data
    public static class PageReqVO extends PageParam {
        private String name;
        private String transport;
        private Integer enabled;
    }

    @Schema(description = "保存入参")
    @Data
    public static class SaveReqVO {
        private Long id;
        @NotBlank(message = "MCP 服务名不能为空")
        private String name;
        @Schema(description = "传输：stdio / sse / streamable-http")
        private String transport;
        private String command;
        @Schema(description = "stdio 参数（JSON 数组字符串）")
        private String args;
        @Schema(description = "stdio 环境变量（JSON 对象字符串；留空=不修改）")
        private String env;
        private String url;
        @Schema(description = "http/sse 请求头（JSON 对象字符串；留空=不修改）")
        private String headers;
        private Integer timeoutSeconds;
        private Integer enabled;
        private String remark;
    }

    @Schema(description = "返回出参（env/headers 已脱敏）")
    @Data
    public static class RespVO {
        private Long id;
        private String name;
        private String transport;
        private String command;
        private String args;
        private String envMask;
        private String url;
        private String headersMask;
        private Integer timeoutSeconds;
        private Integer enabled;
        private String toolSnapshotJson;
        private LocalDateTime lastCheckAt;
        private String lastCheckResult;
        private String remark;
    }

    @Schema(description = "自检/试调入参")
    @Data
    public static class CheckReqVO {
        private Long id;
        @Schema(description = "试调工具名（空=只做 tools/list 自检）")
        private String toolName;
        @Schema(description = "试调入参")
        private Map<String, Object> arguments;
    }

    @Schema(description = "自检/试调出参")
    @Data
    public static class CheckRespVO {
        private Boolean ok;
        private String message;
        private List<Map<String, Object>> tools;
        private Object callResult;
    }
}
