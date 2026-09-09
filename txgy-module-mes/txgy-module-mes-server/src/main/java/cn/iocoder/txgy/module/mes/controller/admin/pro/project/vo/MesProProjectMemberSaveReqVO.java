package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - MES 项目成员新增/修改 Request VO")
@Data
public class MesProProjectMemberSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "成员用户编号（关联系统用户）", example = "1")
    private Long userId;

    @Schema(description = "成员姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotEmpty(message = "成员姓名不能为空")
    private String userName;

    @Schema(description = "项目角色：1项目经理 2生产负责 3技术负责 4质检负责 5其他", example = "1")
    private Integer role;

    @Schema(description = "备注", example = "备注")
    private String remark;

}
