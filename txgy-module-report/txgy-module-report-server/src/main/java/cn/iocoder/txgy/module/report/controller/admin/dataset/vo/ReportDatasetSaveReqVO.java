package cn.iocoder.txgy.module.report.controller.admin.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 报表数据集 - 新增/修改 Request VO
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表数据集新增/修改 Request VO")
@Data
public class ReportDatasetSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "数据集编码（租户内唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "mes_feedback_detail")
    @NotEmpty(message = "数据集编码不能为空")
    private String code;

    @Schema(description = "数据集名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "生产报工明细台账")
    @NotEmpty(message = "数据集名称不能为空")
    private String name;

    @Schema(description = "分类（域）", example = "MES")
    private String category;

    @Schema(description = "业务分组（可多选、可新建；留空则归入「自定义」）", example = "[\"生产\",\"库存\"]")
    private List<String> bizGroups;

    @Schema(description = "SQL 语句（SELECT/WITH 开头，参数用 ${name} 占位）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "SQL 语句不能为空")
    private String sqlText;

    @Schema(description = "参数定义")
    @Valid
    private List<ReportDatasetParamVO> params;

    @Schema(description = "列标签（简单模式自动生成；报表中心据此输出中文表头）")
    @Valid
    private List<ReportDatasetColumnLabelVO> columnLabelList;

    @Schema(description = "租户过滤列名（填写后服务端强制等值过滤）", example = "tenant_id")
    private String tenantColumn;

    @Schema(description = "最大返回行数", example = "5000")
    private Integer maxRows;

    @Schema(description = "查询超时（秒）", example = "30")
    private Integer timeoutSeconds;

    @Schema(description = "状态：0 开启、1 关闭", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "数据集责任人", example = "刘洋")
    private String owner;

    @Schema(description = "备注")
    private String remark;

}
