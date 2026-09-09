package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 招聘应聘投递 Request VO")
@Data
public class HrmPortalRecruitApplyReqVO {
    @NotNull(message = "职位编号不能为空")
    private Long postId;
    @NotBlank(message = "姓名不能为空")
    @Size(max = 255, message = "姓名长度不能超过 255 个字符")
    private String name;
    @NotBlank(message = "手机号码不能为空")
    @Size(max = 18, message = "手机号码长度不能超过 18 个字符")
    @Pattern(regexp = "^(\\+?0?\\d{2,4}-?)?\\d{6,11}$", message = "手机号码格式不正确")
    private String mobile;
    @NotNull(message = "性别不能为空")
    private Integer sex;
    private Integer age;
    @Email(message = "邮箱格式不正确")
    private String email;
    private Integer workTime;
    @NotNull(message = "学历不能为空")
    private Integer education;
    private String graduateSchool;
    private String latestWorkPlace;
    @NotBlank(message = "简历文件名不能为空")
    private String resumeFileName;
    @NotBlank(message = "简历文件地址不能为空")
    private String resumeFileUrl;
    /** 个人中心已保存的简历编号；服务端会重新校验归属并复制到候选人简历管理。 */
    private Long resumeId;
    private String resumeRemark;
    @Size(max = 500, message = "来源说明不能超过 500 个字符")
    private String sourceRemark;
}
