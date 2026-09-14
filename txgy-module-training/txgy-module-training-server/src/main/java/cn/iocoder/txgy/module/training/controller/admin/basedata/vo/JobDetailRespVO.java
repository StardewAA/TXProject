package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 岗位工种全景详情 VO
 *
 * 按《数据字典与一级子模块关联》聚合展示：
 * 工种基础信息 + 该工种资格证要求 + 该工种学习地图（四阶段课程路径）
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 岗位工种全景详情 VO")
@Data
public class JobDetailRespVO {

    @Schema(description = "岗位工种基础信息")
    private JobRespVO job;

    @Schema(description = "该工种的资格证要求")
    private List<JobCertRespVO> certs = new ArrayList<>();

    @Schema(description = "该工种的学习地图（按成长阶段分组）")
    private List<LearningMapStageVO> stages = new ArrayList<>();

}
