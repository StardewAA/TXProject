package cn.iocoder.txgy.module.mes.api.qc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 过程检验单（IPQC）Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 过程检验单 Response DTO")
@Data
public class MesQcIpqcRespDTO {

    @Schema(description = "检验单编号", example = "1024")
    private Long id;

    @Schema(description = "检验单编号", example = "IPQC20250101001")
    private String code;

    @Schema(description = "检验单名称", example = "XX工单过程检验")
    private String name;

    @Schema(description = "IPQC 检验类型", example = "1")
    private Integer type;

    @Schema(description = "来源单据编号", example = "FB20250101001")
    private String sourceDocCode;

    @Schema(description = "生产工单 ID", example = "10")
    private Long workOrderId;

    @Schema(description = "工单编号", example = "WO20250101001")
    private String workOrderCode;

    @Schema(description = "生产任务 ID", example = "20")
    private Long taskId;

    @Schema(description = "工位 ID", example = "30")
    private Long workstationId;

    @Schema(description = "工位名称", example = "工位A")
    private String workstationName;

    @Schema(description = "工序 ID", example = "40")
    private Long processId;

    @Schema(description = "工序名称", example = "组装")
    private String processName;

    @Schema(description = "产品物料 ID", example = "50")
    private Long itemId;

    @Schema(description = "产品物料编码", example = "ITEM001")
    private String itemCode;

    @Schema(description = "产品物料名称", example = "物料A")
    private String itemName;

    @Schema(description = "检测数量", example = "100")
    private BigDecimal checkQuantity;

    @Schema(description = "合格品数量", example = "95")
    private BigDecimal qualifiedQuantity;

    @Schema(description = "不合格品数量", example = "5")
    private BigDecimal unqualifiedQuantity;

    @Schema(description = "工废数量", example = "2")
    private BigDecimal laborScrapQuantity;

    @Schema(description = "料废数量", example = "2")
    private BigDecimal materialScrapQuantity;

    @Schema(description = "其他废品数量", example = "1")
    private BigDecimal otherScrapQuantity;

    @Schema(description = "致命缺陷数量", example = "0")
    private Integer criticalQuantity;

    @Schema(description = "严重缺陷数量", example = "0")
    private Integer majorQuantity;

    @Schema(description = "轻微缺陷数量", example = "1")
    private Integer minorQuantity;

    @Schema(description = "检测结果", example = "1")
    private Integer checkResult;

    @Schema(description = "检测日期")
    private LocalDateTime inspectDate;

    @Schema(description = "检测人员用户 ID", example = "1")
    private Long inspectorUserId;

    @Schema(description = "检测人员昵称", example = "张三")
    private String inspectorNickname;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
