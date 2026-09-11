package cn.iocoder.txgy.module.mes.api.pro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生产工序 Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 生产工序 Response DTO")
@Data
public class MesProProcessRespDTO {

    @Schema(description = "工序编号", example = "1024")
    private Long id;

    @Schema(description = "工序编码", example = "PROCESS001")
    private String code;

    @Schema(description = "工序名称", example = "下料工序")
    private String name;

    @Schema(description = "工艺要求", example = "注意精度")
    private String attention;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
