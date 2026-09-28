package cn.iocoder.txgy.module.training.service.plan;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.RemindRecordPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.RemindRecordRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.RemindReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassStudentDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.RemindRecordDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.StudentTaskDO;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassStudentMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.RemindRecordMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.StudentTaskMapper;
import cn.iocoder.txgy.module.training.enums.plan.ClassStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 培训班督促提醒 Service 实现类（3-4：一键督促 + 站内信）
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class RemindServiceImpl {

    /**
     * 站内信模板编码（见 sql/training_class.sql 兜底模板）
     */
    private static final String TEMPLATE_CODE = "TRAIN_CLASS_STUDY_REMIND";

    @Resource
    private ClassServiceImpl classService;
    @Resource
    private ClassStudentMapper classStudentMapper;
    @Resource
    private StudentTaskMapper studentTaskMapper;
    @Resource
    private RemindRecordMapper remindRecordMapper;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    /**
     * 一键督促：对班级内所有未完成任务的在班学员批量发送站内信，并留痕
     *
     * @return 成功发送的站内信数量
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer remind(RemindReqVO reqVO) {
        ClassDO classDO = classService.exists(reqVO.getClassId());
        if (ClassStatusEnum.FINISHED.getStatus().equals(classDO.getStatus())) {
            throw exception(PLAN_REMIND_CLASS_FINISHED);
        }
        if (ClassStatusEnum.CANCELED.getStatus().equals(classDO.getStatus())) {
            throw exception(PLAN_REMIND_CLASS_NOT_ACTIVE);
        }
        // 1. 圈定督促对象：在班学员中存在未完成任务者
        List<ClassStudentDO> students = classStudentMapper.selectListByClassId(reqVO.getClassId(), 0);
        List<StudentTaskDO> tasks = studentTaskMapper.selectListByClassId(reqVO.getClassId());
        Set<Long> unfinishedUserIds = tasks.stream()
                .filter(item -> !Objects.equals(item.getStatus(), 2))
                .map(StudentTaskDO::getUserId)
                .collect(Collectors.toSet());
        List<ClassStudentDO> targets = students.stream()
                .filter(item -> unfinishedUserIds.contains(item.getUserId()))
                .filter(item -> CollUtil.isEmpty(reqVO.getUserIds()) || reqVO.getUserIds().contains(item.getUserId()))
                .toList();
        if (CollUtil.isEmpty(targets)) {
            throw exception(PLAN_REMIND_NO_TARGET);
        }
        // 2. 逐人发送站内信（模板变量必须齐全，否则 system 拒绝发送）
        String deadlineText = classService.getDeadlineText(classDO);
        int successCount = 0;
        for (ClassStudentDO target : targets) {
            Map<String, Object> params = new HashMap<>();
            params.put("userName", target.getUserName() == null ? String.valueOf(target.getUserId()) : target.getUserName());
            params.put("className", classDO.getName());
            params.put("deadline", deadlineText);
            String content = String.format("您好，%s，您参加的培训班【%s】有学习任务尚未完成（截止时间：%s）。请尽快登录云培训学院完成课程学习，逾期将影响上岗资格核查与结业取证。",
                    params.get("userName"), classDO.getName(), deadlineText);
            RemindRecordDO record = new RemindRecordDO();
            record.setClassId(reqVO.getClassId());
            record.setClassName(classDO.getName());
            record.setUserId(target.getUserId());
            record.setUserName(target.getUserName());
            record.setRemindType(1);
            record.setContent(content);
            try {
                CommonResult<Long> result = notifyMessageSendApi.sendSingleMessageToAdmin(
                        buildSendDTO(target.getUserId(), params));
                if (result != null && result.getCode() == 0 && result.getData() != null) {
                    record.setStatus(0);
                    record.setMessageId(result.getData());
                    successCount++;
                } else {
                    record.setStatus(1);
                    record.setErrorMsg(result == null ? "站内信发送无响应" : result.getMsg());
                }
            } catch (Throwable ex) {
                log.warn("[remind][学员({})督促站内信发送失败]", target.getUserId(), ex);
                record.setStatus(1);
                record.setErrorMsg(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
            remindRecordMapper.insert(record);
        }
        return successCount;
    }

    public PageResult<RemindRecordRespVO> getRemindRecordPage(RemindRecordPageReqVO pageReqVO) {
        classService.exists(pageReqVO.getClassId());
        PageResult<RemindRecordDO> pageResult = remindRecordMapper.selectPage(pageReqVO);
        return cn.iocoder.txgy.framework.common.util.object.BeanUtils.toBean(pageResult, RemindRecordRespVO.class);
    }

    private NotifySendSingleToUserReqDTO buildSendDTO(Long userId, Map<String, Object> params) {
        NotifySendSingleToUserReqDTO reqDTO = new NotifySendSingleToUserReqDTO();
        reqDTO.setUserId(userId);
        reqDTO.setTemplateCode(TEMPLATE_CODE);
        reqDTO.setTemplateParams(params);
        return reqDTO;
    }

}
