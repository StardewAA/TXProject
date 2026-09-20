package cn.iocoder.txgy.module.hrm.service.agent;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeOptimizeApplyReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeVersionCreateReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentOptimizeItemDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentOptimizeItemMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentTaskMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume.HrmPersonalResumeVersionMapper;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentSceneEnum;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentTaskStatusEnum;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeVersionServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_OPTIMIZE_DECISION_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_OPTIMIZE_ORIGINAL_NOT_FOUND;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_OPTIMIZE_TASK_MISMATCH;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.PERSONAL_RESUME_VERSION_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历优化逐项确认测试（A03）。
 *
 * <p>重点：以建议项为最小确认单位、逐项记录原值/建议值/最终值/采纳状态、
 * 不改动源版本、幂等不重复生成版本。</p>
 */
@Import({HrmResumeOptimizeApplyService.class, HrmPersonalResumeVersionServiceImpl.class})
public class HrmResumeOptimizeApplyServiceTest extends BaseDbUnitTest {

    @Resource
    private HrmResumeOptimizeApplyService applyService;
    @Resource
    private HrmPersonalResumeVersionServiceImpl versionService;
    @Resource
    private HrmPersonalResumeVersionMapper versionMapper;
    @Resource
    private HrmRecruitAgentTaskMapper taskMapper;
    @Resource
    private HrmRecruitAgentOptimizeItemMapper optimizeItemMapper;

    private static final String ORIGINAL_DESC = "负责工业管道保温项目的现场施工组织、进度与质量管控";
    private static final String SUGGESTED_DESC = "统筹工业管道保温项目的现场施工组织，负责进度与质量管控，协调甲方与监理";
    private static final String ORIGINAL_ACHIEVEMENT = "累计交付 4 个项目，平均工期缩短 12%";
    private static final String ORIGINAL_STRENGTH = "8 年现场施工管理经验";

    private static final String RESUME_CONTENT = """
            {"skills":"熟悉管道保温施工工艺，持二级建造师证",
             "strengths":"8 年现场施工管理经验",
             "experiences":[{"company":"某某防腐工程有限公司","position":"项目经理",
               "description":"负责工业管道保温项目的现场施工组织、进度与质量管控",
               "achievements":"累计交付 4 个项目，平均工期缩短 12%"}]}
            """;

    /** 准备一个源版本与对应的优化任务 */
    private Long[] prepareSource(Long userId) {
        Long sourceVersionId = versionService.createVersion(userId,
                new HrmPortalResumeVersionCreateReqVO()
                        .setContent(RESUME_CONTENT)
                        .setCreateType(HrmPersonalResumeVersionDO.CREATE_TYPE_MANUAL)
                        .setChangeSummary("初始版本")
                        .setIdempotencyKey("source-" + randomLongId()));

        HrmRecruitAgentTaskDO task = new HrmRecruitAgentTaskDO()
                .setUserId(userId)
                .setScene(HrmRecruitAgentSceneEnum.RESUME_OPTIMIZE.getScene())
                .setBizType("PERSONAL_RESUME_VERSION")
                .setBizId(sourceVersionId)
                .setResumeVersionId(sourceVersionId)
                .setPostId(randomLongId())
                .setIdempotencyKey("task-" + randomLongId())
                .setStatus(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus());
        task.setTenantId(101L);
        TenantUtils.execute(101L, () -> taskMapper.insert(task));
        return new Long[]{sourceVersionId, task.getId()};
    }

    private HrmPortalResumeOptimizeApplyReqVO.Decision decision(String decision,
                                                               String original,
                                                               String suggested,
                                                               String finalValue) {
        return decision("WORK_EXPERIENCE", "description", decision, original, suggested, finalValue);
    }

    private HrmPortalResumeOptimizeApplyReqVO.Decision decision(String module,
                                                               String fieldPath,
                                                               String decision,
                                                               String original,
                                                               String suggested,
                                                               String finalValue) {
        return new HrmPortalResumeOptimizeApplyReqVO.Decision()
                .setModule(module).setFieldPath(fieldPath)
                .setOriginalValue(original).setSuggestedValue(suggested)
                .setFinalValue(finalValue).setDecision(decision);
    }

    private HrmPortalResumeOptimizeApplyReqVO buildReq(Long sourceVersionId, Long taskId,
                                                       String key,
                                                       HrmPortalResumeOptimizeApplyReqVO.Decision... decisions) {
        return new HrmPortalResumeOptimizeApplyReqVO()
                .setSourceVersionId(sourceVersionId).setTaskId(taskId)
                .setIdempotencyKey(key).setDecisions(List.of(decisions));
    }

    @Test
    public void testApply_acceptCreatesNewVersionAndKeepsSourceIntact() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long newVersionId;
        Long sourceVersionId;
        try {
            Long[] prepared = prepareSource(userId);
            sourceVersionId = prepared[0];
            newVersionId = applyService.applyDecisions(userId, buildReq(sourceVersionId, prepared[1],
                    "apply-1",
                    decision("ACCEPT", ORIGINAL_DESC, SUGGESTED_DESC, SUGGESTED_DESC)));
        } finally {
            TenantContextHolder.clear();
        }

        HrmPersonalResumeVersionDO newVersion = TenantUtils.executeIgnore(
                () -> versionMapper.selectByIdAndUserId(newVersionId, userId));
        HrmPersonalResumeVersionDO sourceVersion = TenantUtils.executeIgnore(
                () -> versionMapper.selectByIdAndUserId(sourceVersionId, userId));

        // 新版本：内容已替换，父版本指向源版本，创建方式为确认优化建议
        assertNotEquals(sourceVersionId, newVersionId);
        assertTrue(newVersion.getContent().contains(SUGGESTED_DESC));
        assertFalse(newVersion.getContent().contains(ORIGINAL_DESC));
        assertEquals(sourceVersionId, newVersion.getParentVersionId());
        assertEquals(HrmPersonalResumeVersionDO.CREATE_TYPE_AGENT_OPTIMIZED, newVersion.getCreateType());
        assertEquals(2, newVersion.getVersionNo());
        assertNotNull(newVersion.getChangeSummary());

        // 源版本必须原样保留
        assertTrue(sourceVersion.getContent().contains(ORIGINAL_DESC));
        assertFalse(sourceVersion.getContent().contains(SUGGESTED_DESC));
    }

    @Test
    public void testApply_rejectKeepsContentUnchanged() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long newVersionId;
        try {
            Long[] prepared = prepareSource(userId);
            newVersionId = applyService.applyDecisions(userId, buildReq(prepared[0], prepared[1],
                    "apply-reject",
                    decision("REJECT", ORIGINAL_DESC, SUGGESTED_DESC, ORIGINAL_DESC)));
        } finally {
            TenantContextHolder.clear();
        }

        HrmPersonalResumeVersionDO newVersion = TenantUtils.executeIgnore(
                () -> versionMapper.selectByIdAndUserId(newVersionId, userId));
        // 拒绝项不产生改动，但仍固化出一个新版本
        assertTrue(newVersion.getContent().contains(ORIGINAL_DESC));
        assertFalse(newVersion.getContent().contains(SUGGESTED_DESC));
    }

    @Test
    public void testApply_adjustUsesUserFinalValue() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        String adjusted = "负责工业管道保温项目的现场施工组织与进度管控，协调甲方、监理及分包单位";
        Long newVersionId;
        try {
            Long[] prepared = prepareSource(userId);
            newVersionId = applyService.applyDecisions(userId, buildReq(prepared[0], prepared[1],
                    "apply-adjust",
                    decision("ADJUST", ORIGINAL_DESC, SUGGESTED_DESC, adjusted)));
        } finally {
            TenantContextHolder.clear();
        }

        HrmPersonalResumeVersionDO newVersion = TenantUtils.executeIgnore(
                () -> versionMapper.selectByIdAndUserId(newVersionId, userId));
        assertTrue(newVersion.getContent().contains(adjusted));
    }

    @Test
    public void testApply_isIdempotentForSameKey() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long firstId;
        Long secondId;
        try {
            Long[] prepared = prepareSource(userId);
            HrmPortalResumeOptimizeApplyReqVO reqVO = buildReq(prepared[0], prepared[1], "apply-dup",
                    decision("ACCEPT", ORIGINAL_DESC, SUGGESTED_DESC, SUGGESTED_DESC));
            firstId = applyService.applyDecisions(userId, reqVO);
            secondId = applyService.applyDecisions(userId, reqVO);
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(firstId, secondId);
        // 源版本 + 新版本，共两个版本
        assertEquals(2L, versionService.getVersionList(userId, null).size());
    }

    @Test
    public void testApply_mixedDecisionsApplyOnlyAcceptedAndAdjusted() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long newVersionId;
        try {
            Long[] prepared = prepareSource(userId);
            newVersionId = applyService.applyDecisions(userId, buildReq(prepared[0], prepared[1],
                    "apply-mixed",
                    decision("ACCEPT", ORIGINAL_DESC, SUGGESTED_DESC, SUGGESTED_DESC),
                    decision("REJECT", ORIGINAL_ACHIEVEMENT,
                            "累计成功交付 4 个项目，平均工期缩短 12%", ORIGINAL_ACHIEVEMENT)));
        } finally {
            TenantContextHolder.clear();
        }

        HrmPersonalResumeVersionDO newVersion = TenantUtils.executeIgnore(
                () -> versionMapper.selectByIdAndUserId(newVersionId, userId));
        assertTrue(newVersion.getContent().contains(SUGGESTED_DESC));
        // 被拒绝的那条保持原样
        assertTrue(newVersion.getContent().contains(ORIGINAL_ACHIEVEMENT));
    }

    @Test
    public void testApply_rejectsOriginalValueNotInSource() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                Long[] prepared = prepareSource(userId);
                applyService.applyDecisions(userId, buildReq(prepared[0], prepared[1], "apply-missing",
                        decision("ACCEPT", "这段原文并不存在", "新内容", "新内容")));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_OPTIMIZE_ORIGINAL_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    public void testApply_rejectsInconsistentDecisionState() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);

        // 接受但最终值不等于建议值
        ServiceException acceptException = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                Long[] prepared = prepareSource(userId);
                applyService.applyDecisions(userId, buildReq(prepared[0], prepared[1], "apply-bad-accept",
                        decision("ACCEPT", ORIGINAL_DESC, SUGGESTED_DESC, "完全不同的内容")));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_OPTIMIZE_DECISION_INVALID.getCode(), acceptException.getCode());

        // 拒绝但最终值不等于原值
        ServiceException rejectException = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                Long[] prepared = prepareSource(userId);
                applyService.applyDecisions(userId, buildReq(prepared[0], prepared[1], "apply-bad-reject",
                        decision("REJECT", ORIGINAL_DESC, SUGGESTED_DESC, SUGGESTED_DESC)));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_OPTIMIZE_DECISION_INVALID.getCode(), rejectException.getCode());

        // 未知采纳状态
        ServiceException unknownException = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                Long[] prepared = prepareSource(userId);
                applyService.applyDecisions(userId, buildReq(prepared[0], prepared[1], "apply-unknown",
                        decision("MAYBE", ORIGINAL_DESC, SUGGESTED_DESC, SUGGESTED_DESC)));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_OPTIMIZE_DECISION_INVALID.getCode(), unknownException.getCode());
    }

    @Test
    public void testApply_rejectsAcceptedValueIntroducingNewFact() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        // 接受项再走一次事实护栏：建议值凭空多出一家公司时必须拦截
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                Long[] prepared = prepareSource(userId);
                String withNewOrg = ORIGINAL_DESC + "，服务过中建三局有限公司";
                applyService.applyDecisions(userId, buildReq(prepared[0], prepared[1], "apply-fact",
                        decision("ACCEPT", ORIGINAL_DESC, withNewOrg, withNewOrg)));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_OPTIMIZE_DECISION_INVALID.getCode(), exception.getCode());
    }

    @Test
    public void testApply_rejectsTaskNotMatchingVersion() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                Long[] prepared = prepareSource(userId);
                // 用另一个版本编号搭配该任务
                Long otherVersionId = versionService.createVersion(userId,
                        new HrmPortalResumeVersionCreateReqVO()
                                .setContent(RESUME_CONTENT).setIdempotencyKey("other-" + randomLongId()));
                applyService.applyDecisions(userId, buildReq(otherVersionId, prepared[1], "apply-mismatch",
                        decision("ACCEPT", ORIGINAL_DESC, SUGGESTED_DESC, SUGGESTED_DESC)));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_OPTIMIZE_TASK_MISMATCH.getCode(), exception.getCode());
    }

    @Test
    public void testApply_rejectsOtherUserVersion() {
        Long ownerId = randomLongId();
        Long otherUserId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long[] prepared;
        try {
            prepared = prepareSource(ownerId);
        } finally {
            TenantContextHolder.clear();
        }

        ServiceException exception = assertThrows(ServiceException.class,
                () -> applyService.applyDecisions(otherUserId, buildReq(prepared[0], prepared[1],
                        "apply-cross",
                        decision("ACCEPT", ORIGINAL_DESC, SUGGESTED_DESC, SUGGESTED_DESC))));
        assertEquals(PERSONAL_RESUME_VERSION_NOT_EXISTS.getCode(), exception.getCode());
    }

    /* ===== 采纳情况留痕：清单要求「模块、字段路径、原值、建议值、最终值、采纳状态和排序」 ===== */

    @Test
    public void testApply_recordsDecisionsWithOrderAndFinalValues() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        String adjusted = "8 年工业管道保温项目现场施工管理经验";
        Long newVersionId;
        Long taskId;
        try {
            Long[] prepared = prepareSource(userId);
            taskId = prepared[1];
            newVersionId = applyService.applyDecisions(userId, buildReq(prepared[0], prepared[1],
                    "apply-record",
                    decision("ACCEPT", ORIGINAL_DESC, SUGGESTED_DESC, SUGGESTED_DESC),
                    decision("REJECT", ORIGINAL_ACHIEVEMENT, "累计交付 6 个项目", ORIGINAL_ACHIEVEMENT),
                    decision("SELF_INTRO", "strengths", "ADJUST",
                            ORIGINAL_STRENGTH, "8 年工业管道保温现场施工管理经验", adjusted)));
        } finally {
            TenantContextHolder.clear();
        }

        List<HrmRecruitAgentOptimizeItemDO> items = TenantUtils.executeIgnore(
                () -> optimizeItemMapper.selectListByTaskId(taskId));
        assertEquals(3, items.size());

        HrmRecruitAgentOptimizeItemDO accepted = items.get(0);
        assertEquals(0, accepted.getSeq());
        assertEquals("WORK_EXPERIENCE", accepted.getModule());
        assertEquals("description", accepted.getFieldPath());
        assertEquals(ORIGINAL_DESC, accepted.getOriginalValue());
        assertEquals(SUGGESTED_DESC, accepted.getSuggestedValue());
        assertEquals(SUGGESTED_DESC, accepted.getFinalValue());
        assertEquals("ACCEPT", accepted.getDecision());
        assertEquals(newVersionId, accepted.getResultVersionId());

        HrmRecruitAgentOptimizeItemDO rejected = items.get(1);
        assertEquals(1, rejected.getSeq());
        assertEquals("REJECT", rejected.getDecision());
        // 拒绝项最终值必须等于原值，记录才不失真
        assertEquals(ORIGINAL_ACHIEVEMENT, rejected.getFinalValue());

        HrmRecruitAgentOptimizeItemDO adjustedItem = items.get(2);
        assertEquals(2, adjustedItem.getSeq());
        assertEquals("ADJUST", adjustedItem.getDecision());
        assertEquals(adjusted, adjustedItem.getFinalValue());
    }

    @Test
    public void testApply_doesNotDuplicateDecisionsOnIdempotentResubmit() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long taskId;
        try {
            Long[] prepared = prepareSource(userId);
            taskId = prepared[1];
            HrmPortalResumeOptimizeApplyReqVO reqVO = buildReq(prepared[0], prepared[1], "apply-dup-record",
                    decision("ACCEPT", ORIGINAL_DESC, SUGGESTED_DESC, SUGGESTED_DESC));
            applyService.applyDecisions(userId, reqVO);
            applyService.applyDecisions(userId, reqVO);
        } finally {
            TenantContextHolder.clear();
        }

        List<HrmRecruitAgentOptimizeItemDO> items = TenantUtils.executeIgnore(
                () -> optimizeItemMapper.selectListByTaskId(taskId));
        // 幂等命中同一版本，采纳记录也只应写一次
        assertEquals(1, items.size());
    }
}
