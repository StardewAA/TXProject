package cn.iocoder.txgy.module.training.service.plan;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentAddReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentRandomAddReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentRandomAddRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentScopePreviewRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentScopeReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassStudentDO;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassStudentMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 培训班参与对象筛选 Service 实现类（四定-定人：按部门/岗位圈选参训人员后批量入班）
 *
 * 筛选条件由「部门（可按需含下级部门）+ 岗位（系统岗位）」组成：
 * - 仅填部门：命中该部门（及下级部门）下所有启用账号
 * - 仅填岗位：命中该岗位下所有启用账号
 * - 两者都填：取交集（既在所选部门、又属于所选岗位）
 * - 都未填写：回退培训班创建/编辑时配置的参与对象筛选条件（training_class.target_condition）
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class ClassStudentScopeService {

    /**
     * 学员加入来源：3 按参与对象筛选 4 随机收录
     */
    private static final Integer SOURCE_SCOPE = 3;
    private static final Integer SOURCE_RANDOM = 4;

    @Resource
    private ClassServiceImpl classService;
    @Resource
    private ClassStudentServiceImpl classStudentService;
    @Resource
    private ClassStudentMapper classStudentMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    /**
     * 预览参与对象筛选命中的学员（不落库），供「按参与对象筛选加入」弹窗确认后入班
     */
    public ClassStudentScopePreviewRespVO previewScope(ClassStudentScopeReqVO reqVO) {
        ScopeResult result = resolveScope(reqVO);
        ClassStudentScopePreviewRespVO respVO = new ClassStudentScopePreviewRespVO();
        respVO.setDeptIds(result.deptIds());
        respVO.setPostIds(result.postIds());
        respVO.setIncludeChildDept(result.includeChildDept());
        respVO.setMatchedDeptIds(result.matchedDeptIds());
        // 1. 标记已在班学员，便于前端提示「命中的 N 人中有 M 人已在班」
        Set<Long> inClassUserIds = classStudentMapper.selectListByClassId(reqVO.getClassId(), 0).stream()
                .map(ClassStudentDO::getUserId).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = fetchDeptNameMap(result.users());
        List<ClassStudentScopePreviewRespVO.User> users = new ArrayList<>();
        int inClassCount = 0;
        for (AdminUserRespDTO user : result.users()) {
            ClassStudentScopePreviewRespVO.User item = new ClassStudentScopePreviewRespVO.User();
            item.setUserId(user.getId());
            item.setNickname(user.getNickname());
            item.setDeptName(deptNameMap.get(user.getDeptId()));
            item.setInClass(inClassUserIds.contains(user.getId()));
            if (Boolean.TRUE.equals(item.getInClass())) {
                inClassCount++;
            }
            users.add(item);
        }
        // 2. 可加入的排在前，其次按昵称，便于教务快速确认
        users.sort(Comparator.comparing(ClassStudentScopePreviewRespVO.User::getInClass)
                .thenComparing(ClassStudentScopePreviewRespVO.User::getNickname, Comparator.nullsLast(String::compareTo)));
        respVO.setUsers(users);
        respVO.setMatchedCount(users.size());
        respVO.setInClassCount(inClassCount);
        respVO.setAddableCount(users.size() - inClassCount);
        // 3. 附带在班人数与上限，便于前端提示「已 X/50 人、剩余 Y 个名额」
        respVO.setInClassTotal(inClassUserIds.size());
        respVO.setMaxSize(ClassStudentServiceImpl.MAX_STUDENT_SIZE);
        respVO.setRemaining(Math.max(0, ClassStudentServiceImpl.MAX_STUDENT_SIZE - inClassUserIds.size()));
        return respVO;
    }

    /**
     * 按参与对象筛选条件批量加入学员（自动生成「课程 × 学员」学习任务；已在班幂等跳过）
     *
     * @return 本次实际新增（含恢复在班）人数
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer addStudentsByScope(ClassStudentScopeReqVO reqVO) {
        ScopeResult result = resolveScope(reqVO);
        if (CollUtil.isEmpty(result.users())) {
            throw exception(PLAN_STUDENT_SCOPE_NO_MATCH);
        }
        ClassStudentAddReqVO addReqVO = new ClassStudentAddReqVO();
        addReqVO.setClassId(reqVO.getClassId());
        addReqVO.setUserIds(result.users().stream().map(AdminUserRespDTO::getId).collect(Collectors.toList()));
        addReqVO.setSource(SOURCE_SCOPE);
        return classStudentService.addStudents(addReqVO);
    }

    /**
     * 随机收录学员：按参与对象筛选条件随机抽取人员入班（自动生成「课程 × 学员」学习任务）
     *
     * 单个培训班在班学员上限 50 人：收录人数默认按剩余名额尽量填满，超出剩余名额时自动裁剪；
     * 已在班学员不占用随机名额，可收录人员为「命中人员 - 已在班」。
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassStudentRandomAddRespVO randomAddStudents(ClassStudentRandomAddReqVO reqVO) {
        // 1. 解析筛选条件（入参为空回退培训班已配置条件）并匹配系统用户
        ScopeResult result = resolveScope(reqVO);
        // 2. 统计在班人数与剩余名额
        Set<Long> inClassUserIds = classStudentMapper.selectListByClassId(reqVO.getClassId(), 0).stream()
                .map(ClassStudentDO::getUserId).collect(Collectors.toSet());
        int inClassCount = inClassUserIds.size();
        int remaining = ClassStudentServiceImpl.MAX_STUDENT_SIZE - inClassCount;
        if (remaining <= 0) {
            throw exception(PLAN_CLASS_STUDENT_FULL, ClassStudentServiceImpl.MAX_STUDENT_SIZE);
        }
        // 3. 可收录候选人员 = 命中人员 - 已在班
        List<AdminUserRespDTO> candidates = result.users().stream()
                .filter(user -> !inClassUserIds.contains(user.getId()))
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(candidates)) {
            throw exception(PLAN_STUDENT_SCOPE_NO_ADDABLE);
        }
        // 4. 随机抽取：默认尽量填满剩余名额，指定人数超过剩余名额时自动裁剪
        int expect = reqVO.getCount() == null || reqVO.getCount() <= 0 ? remaining : reqVO.getCount();
        int pickSize = Math.min(Math.min(expect, candidates.size()), remaining);
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        List<AdminUserRespDTO> picked = candidates.subList(0, pickSize);
        // 5. 复用批量添加学员逻辑（自动生成学习任务、已在班幂等跳过），来源标记为「随机收录」
        ClassStudentAddReqVO addReqVO = new ClassStudentAddReqVO();
        addReqVO.setClassId(reqVO.getClassId());
        addReqVO.setUserIds(picked.stream().map(AdminUserRespDTO::getId).collect(Collectors.toList()));
        addReqVO.setSource(SOURCE_RANDOM);
        int added = classStudentService.addStudents(addReqVO);
        // 6. 组装结果，便于前端提示与教务留痕核对
        Map<Long, String> deptNameMap = fetchDeptNameMap(picked);
        ClassStudentRandomAddRespVO respVO = new ClassStudentRandomAddRespVO();
        respVO.setAddedCount(added);
        respVO.setMatchedCount(result.users().size());
        respVO.setAddableCount(candidates.size());
        respVO.setInClassCount(inClassCount + added);
        respVO.setMaxSize(ClassStudentServiceImpl.MAX_STUDENT_SIZE);
        respVO.setRemaining(Math.max(0, remaining - added));
        respVO.setUsers(picked.stream().map(user -> {
            ClassStudentRandomAddRespVO.User item = new ClassStudentRandomAddRespVO.User();
            item.setUserId(user.getId());
            item.setNickname(user.getNickname());
            item.setDeptName(deptNameMap.get(user.getDeptId()));
            return item;
        }).collect(Collectors.toList()));
        return respVO;
    }

    // ========== 私有方法 ==========

    /**
     * 解析筛选条件（入参为空时回退培训班已配置条件）并匹配系统用户
     */
    private ScopeResult resolveScope(ClassStudentScopeReqVO reqVO) {
        ClassDO classDO = classService.exists(reqVO.getClassId());
        ScopeCondition condition = buildCondition(reqVO, classDO);
        if (CollUtil.isEmpty(condition.deptIds()) && CollUtil.isEmpty(condition.postIds())) {
            throw exception(PLAN_STUDENT_SCOPE_CONDITION_REQUIRED);
        }
        // 1. 部门条件：按需展开下级部门
        List<Long> matchedDeptIds = expandDeptIds(condition.deptIds(), condition.includeChildDept());
        // 2. 部门 / 岗位分别匹配；两个条件都填写时取交集
        Map<Long, AdminUserRespDTO> userMap = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(matchedDeptIds)) {
            queryUsers(() -> adminUserApi.getUserListByDeptIds(matchedDeptIds).getCheckedData())
                    .forEach(user -> userMap.put(user.getId(), user));
        }
        if (CollUtil.isNotEmpty(condition.postIds())) {
            List<AdminUserRespDTO> postUsers = queryUsers(
                    () -> adminUserApi.getUserListByPostIds(condition.postIds()).getCheckedData());
            if (CollUtil.isEmpty(matchedDeptIds)) {
                postUsers.forEach(user -> userMap.put(user.getId(), user));
            } else {
                Set<Long> postUserIds = postUsers.stream().map(AdminUserRespDTO::getId).collect(Collectors.toSet());
                userMap.entrySet().removeIf(entry -> !postUserIds.contains(entry.getKey()));
            }
        }
        // 3. 过滤无效 ID 与停用账号：仅可入班的启用账号参与统计
        List<AdminUserRespDTO> users = userMap.values().stream()
                .filter(user -> user.getId() != null)
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .collect(Collectors.toList());
        return new ScopeResult(condition.deptIds(), condition.postIds(), condition.includeChildDept(),
                matchedDeptIds, users);
    }

    /**
     * 组装生效的筛选条件：入参优先，未传则回退培训班已配置的 targetCondition
     */
    private ScopeCondition buildCondition(ClassStudentScopeReqVO reqVO, ClassDO classDO) {
        boolean includeChildDept = !Boolean.FALSE.equals(reqVO.getIncludeChildDept());
        if (CollUtil.isNotEmpty(reqVO.getDeptIds()) || CollUtil.isNotEmpty(reqVO.getPostIds())) {
            return new ScopeCondition(orEmpty(reqVO.getDeptIds()), orEmpty(reqVO.getPostIds()), includeChildDept);
        }
        ClassStudentScopeReqVO saved = JsonUtils.parseObjectQuietly(classDO.getTargetCondition(),
                ClassStudentScopeReqVO.class);
        if (saved == null) {
            return new ScopeCondition(Collections.emptyList(), Collections.emptyList(), includeChildDept);
        }
        return new ScopeCondition(orEmpty(saved.getDeptIds()), orEmpty(saved.getPostIds()),
                saved.getIncludeChildDept() == null ? includeChildDept : saved.getIncludeChildDept());
    }

    /**
     * 展开所选部门的下级部门；查询失败时降级为仅按所选部门筛选
     */
    private List<Long> expandDeptIds(List<Long> deptIds, boolean includeChildDept) {
        if (CollUtil.isEmpty(deptIds)) {
            return Collections.emptyList();
        }
        if (!includeChildDept) {
            return new ArrayList<>(new LinkedHashSet<>(deptIds));
        }
        Set<Long> all = new LinkedHashSet<>(deptIds);
        try {
            List<DeptRespDTO> children = deptApi.getChildDeptList(deptIds).getCheckedData();
            if (CollUtil.isNotEmpty(children)) {
                children.forEach(child -> all.add(child.getId()));
            }
        } catch (Throwable ex) {
            log.warn("[expandDeptIds][查询下级部门失败，降级为仅按所选部门筛选] deptIds={}", deptIds, ex);
        }
        return new ArrayList<>(all);
    }

    /**
     * 查询用户：RPC 异常直接抛出，避免把「查询失败」误报成「未匹配到人员」
     */
    private List<AdminUserRespDTO> queryUsers(Supplier<List<AdminUserRespDTO>> supplier) {
        try {
            return Optional.ofNullable(supplier.get()).orElseGet(Collections::emptyList);
        } catch (Throwable ex) {
            log.warn("[queryUsers][按参与对象筛选查询系统用户失败]", ex);
            throw exception(PLAN_STUDENT_SCOPE_QUERY_FAILED);
        }
    }

    /**
     * 批量获取用户所属部门名称（展示用）；RPC 异常时降级不展示部门
     */
    private Map<Long, String> fetchDeptNameMap(List<AdminUserRespDTO> users) {
        Set<Long> deptIds = users.stream().map(AdminUserRespDTO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (CollUtil.isEmpty(deptIds)) {
            return Collections.emptyMap();
        }
        try {
            return deptApi.getDeptList(deptIds).getCheckedData().stream()
                    .collect(Collectors.toMap(DeptRespDTO::getId, DeptRespDTO::getName, (a, b) -> a));
        } catch (Throwable ex) {
            log.warn("[fetchDeptNameMap][查询部门名称失败，降级不展示部门] deptIds={}", deptIds, ex);
            return Collections.emptyMap();
        }
    }

    private List<Long> orEmpty(List<Long> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 生效的筛选条件
     */
    private record ScopeCondition(List<Long> deptIds, List<Long> postIds, boolean includeChildDept) {
    }

    /**
     * 筛选匹配结果
     */
    private record ScopeResult(List<Long> deptIds, List<Long> postIds, boolean includeChildDept,
                               List<Long> matchedDeptIds, List<AdminUserRespDTO> users) {
    }

}
