package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.EnterpriseMaterialCompletenessRespVO;

/**
 * 企业材料完备性自查服务（身份类 / 资质类 / 实力类 三分类）。
 *
 * <p>规范出处：用户业务口径——按《资质管理规范文件》目录把企业材料分为"企业身份证明 / 企业资质证明 /
 * 企业实力证明"，并"覆盖招投标全流程"；《电子招标投标办法》（20号令）第42条（应如实提交资质证书、
 * 从业人员资格等材料）；《建筑业企业资质管理规定》（住建部令第22号）+《安全生产许可证条例》
 * （国务院令第397号，安许证为施工必备）；《电子证件全流程管理_数据库设计.md》v3.3 表 1（证照台账）、
 * 表 15-1（企业资质）、表 15-4（业绩）、表 15-5（资产/财务）、表 15-8（安许证）。
 * <p>为什么需要它：材料是否齐、是否过期过去靠人工翻台账（Excel 最容易漏项），资格审查却按"缺一项即否决"
 * 执行；本服务把"哪类材料、去哪张表核验、缺什么、何时到期"固化为结构化结论。
 * <p>为什么这样写：材料类别 → 校验来源的映射是固定业务知识，固化在 EnterpriseMaterialTypeEnum 里，
 * 业务代码不散落 if/else；结论是"可重复计算"的只读结果，不落表（避免多一处会过期的不一致来源）。
 * <p>业务目标：投标/申报前一次自查即可发现缺失与临近到期的材料，把风险挡在提交之前。
 */
public interface EnterpriseMaterialService {

    /**
     * 执行完备性自查。
     *
     * @param entId 企业主体 id
     * @param asOf  核验基准日（null 取今天）；证书/资质在其后到期即视为有效
     * @return 三档结论 + 逐项明细（缺失/临近到期清单）
     */
    EnterpriseMaterialCompletenessRespVO checkCompleteness(Long entId, java.time.LocalDate asOf);
}
