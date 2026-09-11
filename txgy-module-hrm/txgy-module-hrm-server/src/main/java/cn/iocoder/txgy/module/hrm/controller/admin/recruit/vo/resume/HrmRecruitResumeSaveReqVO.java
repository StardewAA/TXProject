package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 招聘简历版本新增 Request VO")
@Data
public class HrmRecruitResumeSaveReqVO {

    @NotNull(message = "候选人编号不能为空")
    private Long candidateId;

    @NotBlank(message = "文件名称不能为空")
    @Size(max = 255, message = "文件名称长度不能超过 255 个字符")
    private String fileName;

    @NotBlank(message = "文件地址不能为空")
    @Size(max = 1024, message = "文件地址长度不能超过 1024 个字符")
    private String fileUrl;

    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
