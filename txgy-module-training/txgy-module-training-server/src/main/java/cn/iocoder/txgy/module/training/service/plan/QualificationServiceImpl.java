package cn.iocoder.txgy.module.training.service.plan;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationCheckReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationRecordPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassStudentDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.QualificationRecordDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.StudentTaskDO;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassStudentMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.QualificationRecordMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.StudentTaskMapper;
import cn.iocoder.txgy.module.training.enums.plan.ClassTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 上岗资格核查 Service 实现类（3-5：姓名 + 手机号 → 岗前/复工培训完成情况 → 是否合格 + 通过岗位 + 历史记录）
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
    @Resource
    private QualificationRecordMapper qualificationRecordMapper;

    /**
     * 核查人员上岗资格（姓名 + 手机号）：
     * 1. 按手机号定位平台账号，并校验姓名一致；
     * 2. 找出该人员参加的全部【岗前培训】【复工培训】班级（不限项目），逐班统计任务完成情况；
     * 3. 通过岗位 = 全部任务合格的班级所配置的目标岗位（targetPosts）去重；
     * 4. 岗前培训须存在且合格，且参与的岗前/复工培训均完成 → 判定具备上岗资格；
     * 5. 每次核查写入历史记录（页面下方历史记录列表展示，支持删除 / 批量删除）。
     */
    public QualificationRespVO check(QualificationCheckReqVO reqVO) {
        String mobile = reqVO.getMobile().trim();
        String inputName = reqVO.getUserName().trim();

        AdminUserRespDTO user = getUserByMobile(mobile);

        QualificationRespVO respVO = new QualificationRespVO();
        respVO.setMobile(mobile);
        respVO.setUserName(user == null ? inputName : user.getNickname());
        respVO.setPassedPosts(Collections.emptyList());
        respVO.setItems(Collections.emptyList());

        // 1. 人员必须存在（不抛异常，返回不合格结论，便于对接外部系统）
        if (user == null) {
            respVO.setQualified(false);
            respVO.setReason("该手机号未注册平台账号，暂不具备上岗资格");
            saveRecord(respVO);
            return respVO;
        }
        respVO.setUserId(user.getId());

        // 2. 姓名与手机号须同时匹配，避免误查同名或错号人员
        if (!inputName.equals(user.getNickname())) {
            respVO.setQualified(false);
            respVO.setReason(StrUtil.format(
                    "该手机号对应的账号姓名为「{}」，与输入的「{}」不一致，请核对后重试",
                    user.getNickname(), inputName));
            saveRecord(respVO);
            return respVO;
        }

        // 3. 找出该人员参加的全部岗前/复工培训班（不限项目），逐班核查
        List<ClassStudentDO> students = classStudentMapper.selectListByUserId(user.getId(), 0);
        List<QualificationRespVO.QualificationItemVO> items = new ArrayList<>();
        List<ClassDO> qualifiedClasses = new ArrayList<>();
        boolean preJobPass = false;
        for (ClassStudentDO student : students) {
            ClassDO classDO = classMapper.selectById(student.getClassId());
            if (classDO == null
                    || !(ClassTypeEnum.PRE_JOB.getType().equals(classDO.getType())
                        || ClassTypeEnum.RETURN_WORK.getType().equals(classDO.getType()))) {
                continue; // 仅核查岗前/复工两类培训班
            }
            List<StudentTaskDO> tasks = studentTaskMapper.selectListByClassIdAndUserIds(
                    classDO.getId(), Collections.singletonList(user.getId()));
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
            if (itemQualified) {
                qualifiedClasses.add(classDO);
            }
        }
        respVO.setItems(items);

        // 4. 通过岗位 = 合格班级配置的目标岗位去重
        Set<String> passedPosts = new LinkedHashSet<>();
        for (ClassDO classDO : qualifiedClasses) {
            passedPosts.addAll(parseTargetPosts(classDO.getTargetPosts()));
        }
        respVO.setPassedPosts(new ArrayList<>(passedPosts));

        // 5. 判定与原因
        boolean qualified = false;
        String reason;
        if (items.isEmpty()) {
            reason = "未找到该人员参加的岗前/复工培训记录，暂不具备上岗资格";
        } else if (!preJobPass) {
            reason = "岗前培训缺失或未合格，暂不具备上岗资格";
        } else if (items.stream().anyMatch(item -> !Boolean.TRUE.equals(item.getQualified()))) {
            reason = "存在未完成的岗前/复工培训，暂不具备上岗资格";
        } else {
            qualified = true;
            reason = "岗前培训合格，复工培训合格，具备上岗资格";
        }
        respVO.setQualified(qualified);
        respVO.setReason(qualified && !passedPosts.isEmpty()
                ? reason + "，通过岗位：" + String.join("、", passedPosts)
                : reason);

        // 6. 写入历史记录
        saveRecord(respVO);
        return respVO;
    }

    public PageResult<QualificationRecordDO> getRecordPage(QualificationRecordPageReqVO pageReqVO) {
        return qualificationRecordMapper.selectPage(pageReqVO);
    }

    public void deleteRecord(Long id) {
        qualificationRecordMapper.deleteById(id);
    }

    public void deleteRecordBatch(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        qualificationRecordMapper.delete(new LambdaQueryWrapperX<QualificationRecordDO>()
                .in(QualificationRecordDO::getId, ids));
    }

    private AdminUserRespDTO getUserByMobile(String mobile) {
        try {
            CommonResult<AdminUserRespDTO> result = adminUserApi.getUserByMobile(mobile);
            return result == null ? null : result.getData();
        } catch (Throwable ex) {
            log.warn("[check][按手机号({})获取用户失败]", mobile, ex);
            return null;
        }
    }

    /**
     * 解析培训班目标岗位（targetPosts 兼容字符串数组与对象数组两种历史格式）
     */
    private List<String> parseTargetPosts(String targetPosts) {
        List<String> posts = new ArrayList<>();
        if (StrUtil.isBlank(targetPosts)) {
            return posts;
        }
        try {
            JSONUtil.parseArray(targetPosts).forEach(item -> {
                String name;
                if (item instanceof cn.hutool.json.JSONObject obj) {
                    name = obj.getStr("name");
                } else {
                    name = String.valueOf(item);
                }
                if (StrUtil.isNotBlank(name)) {
                    posts.add(name);
                }
            });
        } catch (Exception ex) {
            log.warn("[parseTargetPosts][解析目标岗位失败：{}]", targetPosts, ex);
        }
        return posts;
    }

    private void saveRecord(QualificationRespVO respVO) {
        try {
            QualificationRecordDO record = new QualificationRecordDO();
            record.setUserId(respVO.getUserId());
            record.setUserName(respVO.getUserName());
            record.setMobile(respVO.getMobile());
            record.setQualified(Boolean.TRUE.equals(respVO.getQualified()));
            record.setPassedPosts(JSONUtil.toJsonStr(respVO.getPassedPosts()));
            record.setReason(respVO.getReason());
            record.setDetailJson(JSONUtil.toJsonStr(respVO.getItems()));
            qualificationRecordMapper.insert(record);
        } catch (Throwable ex) {
            log.warn("[saveRecord][保存上岗资格核查历史记录失败]", ex);
        }
    }

}
