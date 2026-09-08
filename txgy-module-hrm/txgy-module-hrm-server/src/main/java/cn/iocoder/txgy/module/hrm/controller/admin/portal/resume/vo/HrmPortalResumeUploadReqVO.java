package cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 上传个人简历 Request VO")
@Data
public class HrmPortalResumeUploadReqVO {

    @NotBlank(message = "简历标题不能为空")
    @Size(max = 100, message = "简历标题长度不能超过 100 个字符")
    private String title;

    @NotBlank(message = "附件名称不能为空")
    @Size(max = 255, message = "附件名称长度不能超过 255 个字符")
    private String fileName;

    @NotBlank(message = "附件地址不能为空")
    @Size(max = 1024, message = "附件地址长度不能超过 1024 个字符")
    private String fileUrl;

    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
