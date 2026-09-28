package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 培训班学员 Excel 名单解析结果 Response VO
 *
 * <p>用于「批量导入学员并手动指定培训班」：先解析名单（不落库），由教务人工为每位学员选择要加入的培训班后再提交入班。
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 培训班学员 Excel 名单解析结果 Response VO")
@Data
public class ClassStudentImportParseRespVO {

    @Schema(description = "匹配成功的学员人数（Excel 有效数据行）", example = "30")
    private Integer matchedCount;

    @Schema(description = "匹配成功的学员名单（供手动指定培训班）")
    private List<ImportUser> users;

    @Schema(description = "Excel 失败行（手机号为空 / 未匹配到系统账号等）")
    private List<ClassStudentImportRespVO.FailRow> failRows;

    @Schema(description = "解析出的学员")
    @Data
    public static class ImportUser {

        @Schema(description = "Excel 行号（从表头下一行起算）", example = "2")
        private Integer rowNumber;

        @Schema(description = "用户 ID", example = "3001")
        private Long userId;

        @Schema(description = "学员姓名（系统账号昵称）", example = "小王")
        private String userName;

        @Schema(description = "该行手机号", example = "13800000000")
        private String mobile;

        @Schema(description = "部门/班组名称", example = "管道一班")
        private String deptName;

    }

}
