package cn.iocoder.txgy.module.mes.api.pro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工艺路线 Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 工艺路线 Response DTO")
@Data
public class MesProRouteRespDTO {

    @Schema(description = "工艺路线编号", example = "1024")
    private Long id;

    @Schema(description = "工艺路线编码", example = "ROUTE001")
    private String code;

    @Schema(description = "工艺路线名称", example = "标准路线")
    private String name;

    @Schema(description = "工艺路线说明", example = "说明")
    private String description;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
