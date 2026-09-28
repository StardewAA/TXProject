package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 主体档案(自维护)语义见 controller/…/subject 与 docs/法规依据/00-索引；接口规约《招投标模块开发规范》§4.2

import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileSaveReqVO;

/**
 * 主体信息档案服务（T 轨，企业租户自维护：完善本企业 投标人/招标人 信息）。
 *
 * <p>规则（与投标资格门联动）：
 * <ul>
 *   <li>授权 ≠ 完善：仅获得平台授权（bidding_subject_auth）不足以投标/发布，档案必填项全齐（completeFlag=true）方可（需求口径）；</li>
 *   <li>完善度只影响“投标/发布”类资格；关注/订阅/管理操作仅需登录+权限，不受本档案约束（见 NoticeFollow 域）。</li>
 * </ul>
 */
public interface SubjectProfileService {

    /** 本租户主体档案详情（含完善度与缺失项）；未建档返回 null */
    SubjectProfileRespVO getOwnProfile(String subjectType);

    /**
     * 取**本租户主体档案 id**（企业锚点），没有主体档案时返回 null。
     *
     * <p>规范出处：契约里 `entId` 一类"本企业"字段共出现 10 次，而按业务口径"一个租户只有一家公司"
     * （用户已确认），企业主体根本不需要由调用方提供。
     *
     * <p>为什么必须有这个入口：证照/资质的"企业维度锚点"（`org_id`、`ent_id`）此前要么让前端传 `entId`
     * （VO 里甚至没有这个字段，传了被静默忽略 ⇒ `org_id` 恒为 NULL ⇒ 材料完备性永远查不到证照），
     * 要么各服务各写一遍取主体档案的逻辑。这里收敛成**一个入口**：先取 BIDDER（投标人），没有再取 TENDERER（招标人）
     * —— 两个角色指向同一家企业，故谁在都一样。
     *
     * <p>业务目标：让"我是谁"由后端从上下文推出来，前端只传"我要操作哪个对象"。
     */
    Long getOwnSubjectId();

    /** 保存本租户主体档案（无则新建）；统一社会信用代码强校验（GB 32100-2015）并实时核算完善度 */
    Long saveOwnProfile(SubjectProfileSaveReqVO reqVO);

    /**
     * 清除本租户主体档案的营业执照影像（契约《后端变更请求-主体档案影像移除与更换-2026-09-23》B1）。
     *
     * <p><b>为什么需要专用端点</b>：`license_file_id` 在 `save` 里**无法被清空** ——
     * <ol>
     *   <li>前端空值经 `numOrUndef` 变成 `undefined`，`JSON.stringify` 直接丢掉该字段；</li>
     *   <li>即使传 `""`，Jackson 转成 `null`；</li>
     *   <li>而 MyBatis-Plus `updateById` 默认 `NOT_NULL` 策略**忽略 null 字段**，UPDATE 语句里没有这一列 → 原值保留；</li>
     *   <li>更糟的是接口返回 `code=0` 成功，用户以为删掉了（实测确认）。</li>
     * </ol>
     * ⇒ "清空某字段"必须由**专用动作**承担，不能让通用 save 表达。
     *
     * <p>实现遵循模块内已验证的先例：`BidReviewReportMapper#clearGeneratedFilesByTaskId`
     * 用**手写 `@Update` 显式 SET NULL**，而非依赖 `updateById` 的 null 语义。
     *
     * @return true=已执行（含"本就没有影像"的幂等情形，同样返回 true，避免前端把幂等当失败）
     */
    Boolean clearOwnLicenseImage();
}
