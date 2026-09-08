package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCategoryDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "管理后台 - 课程分类 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseCategoryRespVO extends CourseCategoryDO {

    @Schema(description = "子分类（树接口返回）")
    private List<CourseCategoryRespVO> children;

}
