package cn.iocoder.txgy.module.mes.api.pro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生产记录（用户工作站绑定关系快照）Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 生产记录（上下工快照）Response DTO")
@Data
public class MesProWorkRecordRespDTO {

    @Schema(description = "记录编号", example = "1024")
    private Long id;

    @Schema(description = "用户编号", example = "1")
    private Long userId;

    @Schema(description = "用户昵称", example = "张三")
    private String userNickname;

    @Schema(description = "工作站编号", example = "100")
    private Long workstationId;

    @Schema(description = "工作站编码", example = "ST001-2010")
    private String workstationCode;

    @Schema(description = "工作站名称", example = "2010 车间")
    private String workstationName;

    @Schema(description = "当前状态：字典 mes_pro_work_record_type", example = "1")
    private Integer type;

    @Schema(description = "上工时间")
    private LocalDateTime clockInTime;

    @Schema(description = "下工时间")
    private LocalDateTime clockOutTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
