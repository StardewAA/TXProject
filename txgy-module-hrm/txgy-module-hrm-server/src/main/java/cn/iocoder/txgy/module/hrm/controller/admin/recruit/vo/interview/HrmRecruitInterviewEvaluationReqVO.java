package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 面试评估流转 Request VO。 */
@Data
public class HrmRecruitInterviewEvaluationReqVO {

    @NotNull(message = "面试编号不能为空")
    private Long id;

    @NotBlank(message = "评估决定不能为空")
    private String decision;

    @Size(max = 255, message = "面试评价不能超过 255 个字符")
    private String evaluate;
}
