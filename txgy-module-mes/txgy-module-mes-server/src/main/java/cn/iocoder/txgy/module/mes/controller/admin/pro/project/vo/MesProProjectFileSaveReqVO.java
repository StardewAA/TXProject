package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - MES 项目附件新增/修改 Request VO")
@Data
public class MesProProjectFileSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "infra 文件编号", example = "2048")
    private Long fileId;

    @Schema(description = "文件名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "项目合同.pdf")
    private String fileName;

    @Schema(description = "文件访问地址", example = "https://xxx/xxx.pdf")
    private String fileUrl;

    @Schema(description = "文件大小（字节）", example = "102400")
    private Long fileSize;

    @Schema(description = "备注", example = "项目合同扫描件")
    private String remark;

}
