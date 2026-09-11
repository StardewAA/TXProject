package cn.iocoder.txgy.module.mes.api.pro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目 Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 项目 Response DTO")
@Data
public class MesProProjectRespDTO {

    @Schema(description = "项目编号", example = "1024")
    private Long id;

    @Schema(description = "项目编码", example = "PRJ20260902001")
    private String code;

    @Schema(description = "项目名称", example = "某防腐保温工程")
    private String name;

    @Schema(description = "来源单据编号（如销售订单号/报价单号）", example = "SO-001")
    private String orderSourceCode;

    @Schema(description = "项目来源类型：1 销售订单、2 库存备库、3 其他", example = "1")
    private Integer sourceType;

    @Schema(description = "项目状态", example = "0")
    private Integer status;

    @Schema(description = "项目优先级：1 紧急、2 高、3 中、4 低", example = "3")
    private Integer priority;

    @Schema(description = "负责人", example = "张三")
    private String owner;

    @Schema(description = "责任部门", example = "生产管理部")
    private String dept;

    @Schema(description = "计划开始时间")
    private LocalDateTime planStartTime;

    @Schema(description = "计划结束时间")
    private LocalDateTime planEndTime;

    @Schema(description = "实际开始时间")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间")
    private LocalDateTime actualEndTime;

    @Schema(description = "工期天数")
    private Integer durationDays;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
