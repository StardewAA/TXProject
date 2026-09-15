package cn.iocoder.txgy.module.bidding.service.safety;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.KeyPostChangeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.PersonRiskRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.RiskSnapshotRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyGraceRegisterReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyPermitRiskRespVO;

import java.time.LocalDate;

/**
 * 安许 A/B/C 联动风险服务（方案 §1 的核心能力）。
 *
 * <p>规范出处：用户业务口径（黄/红/失效/频繁四态、180 天宽限、劳务分包豁免、企业级与人员级标签分离、
 * 关键岗位频繁变更监控、多岗持证校验、三业务读同一状态）；《电子资质七大模块技术方案.md》§1.2~§1.4；
 * 《资质管理/06-电子证照设计材料对比与推进依据-2026-09-10.md》§二-1/§二-2。
 * <p>为什么做成独立服务而不挂在资质服务下：同一份风险状态要被招投标、施工许可、现场监督检查三方读取，
 * 服务边界按"事实"划分（风险是什么）而不是按"页面"划分，才能保证三方口径一致。
 * <p>业务目标：不达标企业被及时发现与阻断，风险判定可复算、可追溯。
 */
public interface SafetyPermitRiskService {

    /** 统一读取：按企业取安许风险状态（未评估返回 riskFlag=null，业务侧视为不阻断）。 */
    SafetyPermitRiskRespVO getRisk(Long entId, LocalDate asOf);

    /**
     * 重算单个安许证风险。
     *
     * @param permitId 安许证主键
     * @param asOf     评估基准日（空=今天）
     * @param dryRun   true=只算不落库（试算，用于上线核对与人工排查）
     */
    SafetyPermitRiskRespVO evaluate(Long permitId, LocalDate asOf, boolean dryRun);

    /** 全量评估（Job 入口）；返回本次实际评估的证数。 */
    int evaluateAll(LocalDate asOf, boolean dryRun);

    /** 风险快照分页（历史追溯）。 */
    PageResult<RiskSnapshotRespVO> pageSnapshot(Long permitId, Integer pageNo, Integer pageSize);

    /** 人员级风险读取。 */
    PersonRiskRespVO getPersonRisk(Long personId);

    /** 登记整改宽限（原因须在白名单内，天数不得超配置上限）。 */
    void registerGrace(SafetyGraceRegisterReqVO reqVO);

    /** 人员级风险全量评估（Job 入口）；返回被打标的异常人数。 */
    int evaluatePersonnelRisk(LocalDate asOf);

    /** 多岗兼任合规校验（结论落库留痕）。 */
    MultiPostCheckRespVO checkMultiPost(MultiPostCheckReqVO reqVO);

    /** 项目关键岗位变更统计（防挂靠/管理失控）。 */
    KeyPostChangeRespVO getKeyPostChanges(Long projectId, Integer windowDays);
}
