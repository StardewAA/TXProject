package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Schema(description = "管理后台 - MES 项目附件 Response VO")
@Data
@ExcelIgnoreUnannotated
public class MesProProjectFileRespVO {

    @Schema(description = "编号", example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "项目编号", example = "1024")
    private Long projectId;

    @Schema(description = "infra 文件编号", example = "2048")
    private Long fileId;

    @Schema(description = "文件名称", example = "项目合同.pdf")
    @ExcelProperty("文件名称")
    private String fileName;

    @Schema(description = "文件访问地址", example = "https://xxx/xxx.pdf")
    private String fileUrl;

    @Schema(description = "文件大小（字节）", example = "102400")
    @ExcelProperty("文件大小(字节)")
    private Long fileSize;

    @Schema(description = "备注", example = "项目合同扫描件")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
