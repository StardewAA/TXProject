package cn.iocoder.txgy.module.training.service.plan;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationCheckReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassStudentDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.StudentTaskDO;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassStudentMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.StudentTaskMapper;
import cn.iocoder.txgy.module.training.enums.plan.ClassTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.PLAN_QUALIFICATION_USER_NOT_EXISTS;

/**
 * 上岗资格核查 Service 实现类（3-5：人员 ID + 项目 ID → 岗前/复工培训完成情况 → 是否合格）
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class QualificationServiceImpl {

    @Resource
    private ClassMapper classMapper;
    @Resource
    private ClassStudentMapper classStudentMapper;
    @Resource
    private StudentTaskMapper studentTaskMapper;
    @Resource
    private AdminUserApi adminUserApi;

    /**
     * 核查人员在某项目的上岗资格：
     * 1. 找出该项目下该人员参加的【岗前培训】【复工培训】班级；
     * 2. 逐班统计该人员的任务完成情况；
     * 3. 岗前培训须存在且合格，且所有参与的岗前/复工培训均完成 → 判定具备上岗资格。
     */
    public QualificationRespVO check(QualificationCheckReqVO reqVO) {
        QualificationRespVO respVO = new QualificationRespVO();
        respVO.setUserId(reqVO.getUserId());
        respVO.setProjectId(reqVO.getProjectId());

        // 校验人员存在性：不存在或查询失败时仍返回不合格结论，不抛异常（便于外部系统对接）
        AdminUserRespDTO user;
        try {
            user = getUser(reqVO.getUserId());
            respVO.setUserName(user == null ? null : user.getNickname());
        } catch (cn.iocoder.txgy.framework.common.exception.ServiceException ex) {
            respVO.setUserName(null);
            respVO.setQualified(false);
            respVO.setReason("人员不存在或已停用，暂不具备上岗资格");
            respVO.setItems(Collections.emptyList());
            return respVO;
        }

        List<ClassDO> classList = classMapper.selectListByProjectAndTypes(reqVO.getProjectId(),
                List.of(ClassTypeEnum.PRE_JOB.getType(), ClassTypeEnum.RETURN_WORK.getType()));
        List<QualificationRespVO.QualificationItemVO> items = new ArrayList<>();
        boolean preJobPass = false;
        for (ClassDO classDO : classList) {
            ClassStudentDO student = classStudentMapper.selectByClassIdAndUserId(classDO.getId(), reqVO.getUserId());
            if (student == null || !Objects.equals(student.getStatus(), 0)) {
                continue; // 未参加该班（或已移出），不计入
            }
            List<StudentTaskDO> tasks = studentTaskMapper.selectListByClassIdAndUserIds(
                    classDO.getId(), Collections.singletonList(reqVO.getUserId()));
            int total = tasks.size();
            long finished = tasks.stream().filter(item -> Objects.equals(item.getStatus(), 2)).count();
            boolean itemQualified = total > 0 && finished == total;
            if (ClassTypeEnum.PRE_JOB.getType().equals(classDO.getType()) && itemQualified) {
                preJobPass = true;
            }
            QualificationRespVO.QualificationItemVO item = new QualificationRespVO.QualificationItemVO();
            item.setClassId(classDO.getId());
            item.setClassName(classDO.getName());
            item.setType(classDO.getType());
            item.setTypeName(ClassTypeEnum.getName(classDO.getType()));
            item.setClassStatus(classDO.getStatus());
            item.setDeadline(classDO.getDeadline());
            item.setTotalTasks(total);
            item.setFinishedTasks((int) finished);
            item.setQualified(itemQualified);
            items.add(item);
        }
        respVO.setItems(items);
        // 3. 判定与原因
        boolean qualified = false;
        String reason;
        if (items.isEmpty()) {
            reason = "未找到该人员在本项目下的岗前/复工培训记录，暂不具备上岗资格";
        } else if (!preJobPass) {
            reason = "岗前培训缺失或未合格，暂不具备上岗资格";
        } else if (items.stream().anyMatch(item -> !Boolean.TRUE.equals(item.getQualified()))) {
            reason = "存在未完成的岗前/复工培训，暂不具备上岗资格";
        } else {
            qualified = true;
            reason = "岗前培训合格，复工培训合格，具备上岗资格";
        }
        respVO.setQualified(qualified);
        respVO.setReason(reason);
        return respVO;
    }

    private AdminUserRespDTO getUser(Long userId) {
        try {
            CommonResult<AdminUserRespDTO> result = adminUserApi.getUser(userId);
            AdminUserRespDTO user = result == null ? null : result.getData();
            if (user == null) {
                throw exception(PLAN_QUALIFICATION_USER_NOT_EXISTS);
            }
            return user;
        } catch (cn.iocoder.txgy.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Throwable ex) {
            log.warn("[check][获取用户({})失败，降级放行]", userId, ex);
            return null;
        }
    }

}
