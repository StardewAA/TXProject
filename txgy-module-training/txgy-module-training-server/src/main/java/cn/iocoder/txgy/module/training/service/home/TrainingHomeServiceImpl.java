package cn.iocoder.txgy.module.training.service.home;

import cn.iocoder.txgy.module.training.controller.admin.home.vo.TrainingHomeOverviewRespVO;
import cn.iocoder.txgy.module.training.controller.admin.home.vo.TrainingModuleRespVO;
import org.springframework.stereotype.Service;

import java.util.List;

/** 云培训学院工作台服务实现。 */
@Service
public class TrainingHomeServiceImpl implements TrainingHomeService {

    @Override
    public TrainingHomeOverviewRespVO getOverview() {
        List<TrainingModuleRespVO> modules = List.of(
                module("resource", "资源中心", "统一沉淀企业培训内容与知识资产", "视频与课件", "知识分类", "资源审核"),
                module("course", "课程管理", "完成课程设计、发布与全生命周期管理", "课程编排", "版本管理", "学习规则"),
                module("training", "培训管理", "覆盖培训计划、组织实施和过程跟踪", "培训计划", "报名管理", "效果评估"),
                module("learning", "学员学习中心", "为学员提供统一的学习任务和进度入口", "学习任务", "进度跟踪", "学习笔记"),
                module("exam", "考试与认证", "构建考试测评、证书发放与到期管理闭环", "题库管理", "在线考试", "证书管理"),
                module("live", "直播与讲师体系", "管理直播课堂、讲师资源和授课质量", "直播课堂", "讲师档案", "授课评价"),
                module("vr", "VR 虚拟仿真实操训练", "面向高风险作业开展可重复的沉浸式实训", "VR 场景", "实操任务", "过程评分"),
                module("cooperation", "校企合作与创新大赛", "连接院校、企业和赛事项目的协同运营", "合作项目", "赛事管理", "成果转化"),
                module("analytics", "数据统计与档案归档", "汇总学习成效并形成可追溯的培训档案", "数据看板", "培训档案", "合规报表")
        );
        return new TrainingHomeOverviewRespVO(
                "云培训学院",
                "面向企业员工、讲师和管理人员的一体化数字培训平台",
                modules.size(),
                modules
        );
    }

    private TrainingModuleRespVO module(String key, String name, String description, String... capabilities) {
        return new TrainingModuleRespVO(key, name, description, List.of(capabilities));
    }
}
