package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 求职助理对话里附带的岗位卡片。
 *
 * <p>只带卡片上真的要显示的字段。复用门户职位 VO 会把职位描述、经纬度、招聘负责人
 * 这些对话里用不到的东西一起发出去，纯属浪费带宽。</p>
 */
@Schema(description = "员工端 - 求职助理对话附带的岗位卡片")
@Data
@Accessors(chain = true)
public class HrmPortalRecruitAgentChatJobVO {

    @Schema(description = "职位编号，前端点卡片进详情页要用")
    private Long id;

    @Schema(description = "职位名称", example = "防腐工")
    private String postName;

    @Schema(description = "工作城市", example = "陕西省 / 延安市")
    private String areaName;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "学历要求")
    private String education;

    @Schema(description = "工作经验要求")
    private String experience;

    @Schema(description = "薪资文案，面议时为「面议」", example = "8-12K")
    private String salary;
}
