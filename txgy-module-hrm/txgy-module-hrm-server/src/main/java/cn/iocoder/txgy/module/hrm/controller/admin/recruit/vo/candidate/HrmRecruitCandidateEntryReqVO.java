package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.candidate;

import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeSaveReqVO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - HRM 招聘候选人转员工 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitCandidateEntryReqVO extends HrmEmployeeSaveReqVO {

    @Schema(description = "应聘记录编号；新流程入职必须提供", example = "1024")
    private Long applicationId;

    @AssertTrue(message = "候选人编号不能为空")
    @JsonIgnore
    public boolean isCandidateIdValid() {
        return getCandidateId() != null;
    }

}
