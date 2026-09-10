package cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 创建个人简历 Request VO")
@Data
public class HrmPortalResumeCreateReqVO {

    @NotBlank(message = "简历标题不能为空")
    @Size(max = 100, message = "简历标题长度不能超过 100 个字符")
    private String title;

    @Size(max = 5000, message = "简历内容长度不能超过 5000 个字符")
    private String content;

    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
