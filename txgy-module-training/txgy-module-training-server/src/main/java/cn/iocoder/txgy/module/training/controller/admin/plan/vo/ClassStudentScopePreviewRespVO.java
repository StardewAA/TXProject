package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 培训班参与对象筛选命中人员 Response VO")
@Data
public class ClassStudentScopePreviewRespVO {

    @Schema(description = "生效的部门 ID 列表", example = "[100,101]")
    private List<Long> deptIds;

    @Schema(description = "生效的岗位 ID 列表", example = "[3]")
    private List<Long> postIds;

    @Schema(description = "是否包含下级部门", example = "true")
    private Boolean includeChildDept;

    @Schema(description = "展开下级部门后实际参与筛选的部门 ID 列表", example = "[100,101,102]")
    private List<Long> matchedDeptIds;

    @Schema(description = "命中人员总数", example = "12")
    private Integer matchedCount;

    @Schema(description = "命中人员中已在班人数", example = "4")
    private Integer inClassCount;

    @Schema(description = "可加入人数（命中人员 - 已在班）", example = "8")
    private Integer addableCount;

    @Schema(description = "命中人员明细")
    private List<User> users;

    @Schema(description = "命中人员明细")
    @Data
    public static class User {

        @Schema(description = "用户 ID", example = "3001")
        private Long userId;

        @Schema(description = "用户昵称", example = "小王")
        private String nickname;

        @Schema(description = "部门/班组名称", example = "管道一班")
        private String deptName;

        @Schema(description = "是否已在班", example = "false")
        private Boolean inClass;

    }

}
