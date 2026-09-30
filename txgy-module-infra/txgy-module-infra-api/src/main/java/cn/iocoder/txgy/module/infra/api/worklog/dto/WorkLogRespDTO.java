package cn.iocoder.txgy.module.infra.api.worklog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作留痕 RPC 响应 DTO
 *
 * <p>仅供业务模块（如 WMS 的 AI 助手）读取**当前登录用户自己**的操作留痕；
 * 不提供「查询指定用户」的能力，从接口设计上杜绝越权读取他人操作记录。
 *
 * @author 天信管业
 */
@Schema(description = "RPC 服务 - 工作留痕")
@Data
public class WorkLogRespDTO {

    @Schema(description = "留痕编号")
    private Long id;

    @Schema(description = "模块", example = "wms")
    private String module;

    @Schema(description = "动作", example = "CREATE")
    private String action;

    @Schema(description = "摘要", example = "创建入库单")
    private String summary;

    @Schema(description = "业务键", example = "RK09216038")
    private String businessKey;

    @Schema(description = "请求方式", example = "POST")
    private String requestMethod;

    @Schema(description = "请求地址", example = "/admin-api/wms/receipt-order/create")
    private String requestUrl;

    @Schema(description = "结果码（0 表示成功）", example = "0")
    private Integer resultCode;

    @Schema(description = "耗时（毫秒）")
    private Integer duration;

    @Schema(description = "操作时间")
    private LocalDateTime beginTime;

}
