package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 招聘企业产品/服务")
@Data
public class HrmRecruitCompanyProductVO {

    @Schema(description = "名称")
    @Size(max = 128)
    private String name;

    @Schema(description = "介绍")
    @Size(max = 1000)
    private String intro;

    @Schema(description = "图片地址")
    @Size(max = 500)
    private String image;
}
