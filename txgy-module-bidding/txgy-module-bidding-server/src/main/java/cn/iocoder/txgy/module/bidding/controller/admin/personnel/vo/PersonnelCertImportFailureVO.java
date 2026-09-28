package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 员工证书导入失败行")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonnelCertImportFailureVO {

    @Schema(description = "行号（从 2 起，表头为第 1 行）", example = "3")
    private Integer rowNum;

    @Schema(description = "失败原因", example = "证书编号不能为空")
    private String message;
}
