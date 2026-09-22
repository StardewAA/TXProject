package cn.iocoder.txgy.module.report.controller.admin.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表数据集 - Response VO
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表数据集 Response VO")
@Data
public class ReportDatasetRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "数据集编码", example = "mes_feedback_detail")
    private String code;

    @Schema(description = "数据集名称", example = "生产报工明细台账")
    private String name;

    @Schema(description = "分类（域）", example = "MES")
    private String category;

    @Schema(description = "业务分组（支持多归属）", example = "[\"生产\",\"库存\"]")
    private List<String> bizGroups;

    @Schema(description = "SQL 语句")
    private String sqlText;

    @Schema(description = "参数定义")
    private List<ReportDatasetParamVO> params;

    @Schema(description = "列标签（简单模式自动生成）")
    private List<ReportDatasetColumnLabelVO> columnLabelList;

    @Schema(description = "租户过滤列名")
    private String tenantColumn;

    @Schema(description = "最大返回行数", example = "5000")
    private Integer maxRows;

    @Schema(description = "查询超时（秒）", example = "30")
    private Integer timeoutSeconds;

    @Schema(description = "状态：0 开启、1 关闭", example = "0")
    private Integer status;

    @Schema(description = "数据集责任人", example = "刘洋")
    private String owner;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "租户编号（0 为平台级）", example = "0")
    private Long tenantId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
