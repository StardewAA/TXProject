package cn.iocoder.txgy.module.bidding.service.review;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskStatsRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.review.ReviewTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.review.ReviewTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.ReviewTaskStatusEnum;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台审查任务服务测试（Mockito，TDD）。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§3 状态机 / §4 权限与租户隔离 / §6 乐观锁。
 *
 * <p><b>为什么专门为"企业主体过滤"写用例</b>：联调时真实踩过一个**静默缺陷** ——
 * 服务端把 `ent_id`（企业主体档案 id）误当成 `tenant_id` 来比较。这类错误不会抛异常、不会报错，
 * 只是让租户视角的列表永远为空（total=0），平台视角却一切正常，因此**极难在人工点测中发现**。
 * 用例把口径钉死：过滤条件必须落在 `ent_id`，且必须来自 `SubjectProfileService#getOwnSubjectId`。
 *
 * <p>业务目标：企业录入即入审查队列；租户只看本企业；超管统一审查；结论可追溯。
 *
 * @author txgy
 */
class ReviewTaskServiceImplTest {

    private ReviewTaskMapper reviewTaskMapper;
    private SubjectProfileService subjectProfileService;
    private ReviewTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        // lambda 列名缓存必须先就绪，否则 wrapper 一旦解析列名就抛 can not find lambda cache（见工具类注释）
        MybatisPlusLambdaCache.init(ReviewTaskDO.class);
        reviewTaskMapper = mock(ReviewTaskMapper.class);
        subjectProfileService = mock(SubjectProfileService.class);
        service = new ReviewTaskServiceImpl();
        ReflectionTestUtils.setField(service, "reviewTaskMapper", reviewTaskMapper);
        ReflectionTestUtils.setField(service, "subjectProfileService", subjectProfileService);
    }

    @Test
    void createTask_persistsPendingAndSubmitTime() {
        // 规范出处：§3 —— 企业录入即产生 PENDING 任务；时间由服务端落库（不信前端时钟）。
        when(reviewTaskMapper.insert(any(ReviewTaskDO.class))).thenAnswer(inv -> {
            ReviewTaskDO d = inv.getArgument(0);
            d.setId(1001L);
            return 1;
        });

        Long id = service.createTask("CERTIFICATE", 7L, 182143816674785L, "示例企业", "91330100MA2E2E001X", "XZ-2026-001", null);

        ArgumentCaptor<ReviewTaskDO> captor = ArgumentCaptor.forClass(ReviewTaskDO.class);
        verify(reviewTaskMapper).insert(captor.capture());
        ReviewTaskDO saved = captor.getValue();
        assertEquals(1001L, id, "应回传插入后的任务 id");
        assertEquals(ReviewTaskStatusEnum.PENDING.name(), saved.getStatus(), "新任务必须是 PENDING");
        assertEquals("CERTIFICATE", saved.getBizType());
        assertEquals(7L, saved.getBizId());
        assertEquals(182143816674785L, saved.getEntId(), "ent_id 存的是**企业主体 id**，不是租户号");
        assertNotNull(saved.getSubmitTime(), "提交时间由服务端生成");
    }

    @Test
    void getTaskPage_tenantScoped_usesOwnSubjectIdForEnterpriseFilter() {
        // 规范出处：§4 租户隔离 —— 可见范围由后端按登录身份决定。
        //
        // 这条用例锁死的是一个**静默缺陷**：服务端曾把 ent_id（企业主体档案 id）当成 tenant_id 比较，
        // 结果租户视角列表恒为空（total=0）却不报任何错，平台视角又完全正常 —— 人工点测几乎发现不了。
        //
        // 断言方式：MyBatis-Plus 的 LambdaQueryWrapper 需要 TableInfo 缓存才能把方法引用解析成列名，
        // 纯 Mockito（无 Spring 容器）下缓存为空会抛 "can not find lambda cache"。
        // 故此处不去解析 SQL，而是直接断言"企业锚点入口被以本企业身份调用过" —— 这正是该缺陷的判别点：
        //   缺陷版：从不调用 getOwnSubjectId()（改用 TenantContextHolder）
        //   修复版：调用 getOwnSubjectId() 并把返回值作为过滤值
        when(subjectProfileService.getOwnSubjectId()).thenReturn(182143816674785L);
        // 给一条记录：BeanUtils 对空 Page 的转换结果不可靠，用真实数据聚焦"过滤口径"这一被测点
        ReviewTaskDO row = new ReviewTaskDO();
        row.setId(1L);
        row.setEntId(182143816674785L);
        row.setStatus(ReviewTaskStatusEnum.PENDING.name());
        // 注意匹配的是 BaseMapperX#selectPage(PageParam, Wrapper) 这个**重载**，且它返回 PageResult（不是 Page）：
        // 服务传的是 PageReqVO（PageParam）；Mockito 不执行 default 方法，按 selectPage(Page,...) 打桩不会命中，
        // 被测方法会拿到 null —— 这正是先前 "expected: not <null>" 的原因。
        PageResult<ReviewTaskDO> page = new PageResult<>(java.util.List.of(row), 1L);
        when(reviewTaskMapper.selectPage(any(cn.iocoder.txgy.framework.common.pojo.PageParam.class), any()))
                .thenReturn(page);

        PageResult<ReviewTaskRespVO> result = service.getTaskPage(new ReviewTaskPageReqVO(), true);

        assertNotNull(result, "服务应正常返回分页结果（而非因过滤口径错误得到空页）");
        assertEquals(1, result.getList().size());
        verify(subjectProfileService).getOwnSubjectId();
        // 且该值确实被当作查询条件传给了 mapper。
        // 断言取值路径：selectPage 的第 2 个实参是 wrapper；用 AbstractWrapper 取值参数表，
        // 断"过滤值 = 企业主体 id"而不是"tenant_id"。这正是不调 getOwnSubjectId() 的缺陷版会失败之处。
        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(reviewTaskMapper).selectPage(
                any(cn.iocoder.txgy.framework.common.pojo.PageParam.class),
                (com.baomidou.mybatisplus.core.conditions.Wrapper<ReviewTaskDO>) captor.capture());
        var wrapper = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ReviewTaskDO, ?>)
                captor.getValue();
        // 读 wrapper 渲染的 SQL 片段来确认过滤列。
        // 说明：此处片段是 MyBatis-Plus 的**实体属性名**（entId），
        // 真正下发前会经 TableInfo 映射成列名 ent_id；两者指向同一字段，断言属性名即可。
        // 缺陷版（用 TenantContextHolder）渲染出的会是 tenantId —— 断言据此区分修复前后。
        String sqlSegment = wrapper.getTargetSql();
        org.junit.jupiter.api.Assertions.assertTrue(
                sqlSegment.contains("entId = ?"),
                "租户视角必须按企业主体字段 entId 过滤（而非 tenantId），实际 SQL 片段=" + sqlSegment);
        org.junit.jupiter.api.Assertions.assertFalse(
                sqlSegment.contains("tenantId"),
                "租户条件由 MyBatis-Plus 租户插件负责，服务层不应再手写 tenantId，实际 SQL 片段=" + sqlSegment);
        org.junit.jupiter.api.Assertions.assertTrue(
                wrapper.getParamNameValuePairs().containsValue(182143816674785L),
                "租户视角的过滤值必须是企业主体 id（182143816674785），实际 SQL 片段=" + sqlSegment
                        + "，参数=" + wrapper.getParamNameValuePairs());
    }

    @Test
    void getTaskPage_platformScoped_doesNotFilterByEnterprise() {
        // 规范出处：§4/§5 —— 平台视角跨企业筛选，不应被"本企业"条件限制。
        when(reviewTaskMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 5));

        service.getTaskPage(new ReviewTaskPageReqVO(), false);

        org.mockito.Mockito.verify(subjectProfileService, org.mockito.Mockito.never()).getOwnSubjectId();
    }

    @Test
    void getTask_tenantScoped_otherEnterpriseReadsAsNotExists() {
        // 规范出处：§4 —— 越权读取按"不存在"处理，不泄露他企业任务是否存在（避免枚举探测）。
        ReviewTaskDO other = new ReviewTaskDO();
        other.setId(9L);
        other.setEntId(999L);
        when(reviewTaskMapper.selectById(9L)).thenReturn(other);
        when(subjectProfileService.getOwnSubjectId()).thenReturn(182143816674785L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getTask(9L, true));
        assertEquals(ErrorCodeConstants.REVIEW_TASK_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void getStats_tenantScoped_countsOwnEnterpriseOnly() {
        // 规范出处：§5 —— 统计口径与列表同源，否则会出现"列表 1 条、统计 0 条"的自相矛盾。
        when(subjectProfileService.getOwnSubjectId()).thenReturn(182143816674785L);
        when(reviewTaskMapper.selectCount(any())).thenReturn(1L);

        ReviewTaskStatsRespVO stats = service.getStats(true);

        assertNotNull(stats);
        assertEquals(1L, stats.getPending());
    }

    @Test
    void reject_blankReasonRejectedServerSide() {
        // 规范出处：§3 —— 驳回原因必填且**租户可见**，是整改依据；服务端必须再校验一次，
        // 不能只依赖 VO 上的 @NotBlank（前端可绕过，内部调用也不会走校验）。
        ReviewTaskDO pending = new ReviewTaskDO();
        pending.setId(5L);
        pending.setStatus(ReviewTaskStatusEnum.PENDING.name());
        pending.setVersion(0);
        when(reviewTaskMapper.selectById(5L)).thenReturn(pending);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.reject(5L, 0, "   ", "超管"));
        assertEquals(ErrorCodeConstants.REVIEW_TASK_REASON_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void approve_casConflict_throwsModified() {
        // 规范出处：§6 乐观锁 —— version 不匹配（他人已先审）必须报错，绝不静默覆盖结论。
        ReviewTaskDO pending = new ReviewTaskDO();
        pending.setId(6L);
        pending.setStatus(ReviewTaskStatusEnum.PENDING.name());
        pending.setVersion(0);
        when(reviewTaskMapper.selectById(6L)).thenReturn(pending);
        // 影响行数 0 == CAS 失败（期间被他人改过）
        when(reviewTaskMapper.update(isNull(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.approve(6L, 0, "超管"));
        assertEquals(ErrorCodeConstants.REVIEW_TASK_MODIFIED.getCode(), ex.getCode());
    }

    @Test
    void approve_alreadyReviewed_throwsStatusInvalid() {
        // 规范出处：§3 状态机 —— 只允许 PENDING → APPROVED/REJECTED，重复审查报状态非法。
        ReviewTaskDO approved = new ReviewTaskDO();
        approved.setId(7L);
        approved.setStatus(ReviewTaskStatusEnum.APPROVED.name());
        approved.setVersion(3);
        when(reviewTaskMapper.selectById(7L)).thenReturn(approved);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.approve(7L, 3, "超管"));
        assertEquals(ErrorCodeConstants.REVIEW_TASK_STATUS_INVALID.getCode(), ex.getCode());
    }
}
