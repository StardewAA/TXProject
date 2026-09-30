package cn.iocoder.txgy.module.hrm.service.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 某个应聘记录最近一次成功的「应聘匹配」结果摘要。
 *
 * <p>只带列表需要的分数与任务编号：完整结论由前端按 taskId 另查
 * {@code /hrm/recruit/agent/task/get}，避免沟通页列表接口塞入大段 JSON。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HrmRecruitAgentMatchScoreBO {

    /** 应聘记录编号 */
    private Long applicationId;

    /** 最近一次成功的应聘匹配任务编号 */
    private Long taskId;

    /** 匹配总分 0-100 */
    private Integer overallScore;
}
