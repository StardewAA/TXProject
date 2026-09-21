package cn.iocoder.txgy.module.report.controller.admin.dataset.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 报表数据集 - 分页查询 Request VO
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表数据集分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ReportDatasetPageReqVO extends PageParam {

    @Schema(description = "数据集编码（模糊匹配）", example = "mes_")
    private String code;

    @Schema(description = "数据集名称（模糊匹配）", example = "报工")
    private String name;

    @Schema(description = "分类（域）", example = "MES")
    private String category;

    @Schema(description = "状态：0 开启、1 关闭", example = "0")
    private Integer status;

}
