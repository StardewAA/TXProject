package cn.iocoder.txgy.module.mes.api.pro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安灯呼叫记录 Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 安灯呼叫记录 Response DTO")
@Data
public class MesProAndonRecordRespDTO {

    @Schema(description = "安灯呼叫记录编号", example = "1024")
    private Long id;

    @Schema(description = "工作站编号", example = "100")
    private Long workstationId;

    @Schema(description = "工作站编码", example = "ST001-2010")
    private String workstationCode;

    @Schema(description = "工作站名称", example = "2010 车间")
    private String workstationName;

    @Schema(description = "发起用户编号", example = "1")
    private Long userId;

    @Schema(description = "发起用户昵称", example = "张三")
    private String userNickname;

    @Schema(description = "处置人编号", example = "1")
    private Long handlerUserId;

    @Schema(description = "处置人昵称", example = "李四")
    private String handlerNickname;

    @Schema(description = "生产工单编号", example = "100")
    private Long workOrderId;

    @Schema(description = "工序编号", example = "200")
    private Long processId;

    @Schema(description = "工序名称", example = "下料")
    private String processName;

    @Schema(description = "呼叫原因（快照值）", example = "设备故障")
    private String reason;

    @Schema(description = "呼叫级别（快照值）", example = "1")
    private Integer level;

    @Schema(description = "处置状态", example = "0")
    private Integer status;

    @Schema(description = "处置时间")
    private LocalDateTime handleTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
