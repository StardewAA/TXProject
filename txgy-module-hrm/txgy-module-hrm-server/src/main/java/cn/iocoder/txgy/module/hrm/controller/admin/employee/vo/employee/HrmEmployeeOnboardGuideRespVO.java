package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 管理后台 - HRM 员工「上岗向导」Response VO（I-020）
 *
 * <p>把「员工入职后必须配齐的四件事」——后台账号、薪资（薪资组 + 薪资档案）、考勤组、
 * 社保参保——聚合成一份就绪清单：每一项给出完成状态、现状说明与一键跳转目标页，
 * HR 不必再到四个模块里逐个确认。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - HRM 员工上岗向导 Response VO")
@Data
@Accessors(chain = true)
public class HrmEmployeeOnboardGuideRespVO {

    @Schema(description = "员工编号")
    private Long employeeId;

    @Schema(description = "员工姓名")
    private String employeeName;

    @Schema(description = "工号")
    private String jobNumber;

    @Schema(description = "已完成项数")
    private Integer readyCount;

    @Schema(description = "总项数")
    private Integer totalCount;

    @Schema(description = "是否全部就绪")
    private Boolean ready;

    @Schema(description = "清单项")
    private List<Item> items;

    @Schema(description = "上岗向导清单项")
    @Data
    @Accessors(chain = true)
    public static class Item {

        @Schema(description = "标识：account / salary / attendance / insurance")
        private String key;

        @Schema(description = "标题")
        private String title;

        @Schema(description = "是否已完成")
        private Boolean done;

        @Schema(description = "状态文案")
        private String statusText;

        @Schema(description = "现状说明（如薪资组名称、参保方案）")
        private String detail;

        @Schema(description = "一键跳转的前端路径")
        private String actionPath;

        @Schema(description = "跳转按钮文案")
        private String actionText;

    }

}
