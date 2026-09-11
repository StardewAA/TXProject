package cn.iocoder.txgy.module.training.controller.portal.learner;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerCourseCardRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerCourseDetailRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.MyCoursesGroupRespVO;
import cn.iocoder.txgy.module.training.enums.learner.LearnerTaskStatusEnum;
import cn.iocoder.txgy.module.training.service.learner.LearnerCourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "学员端 - 学员学习中心-我的课程")
@RestController
@RequestMapping("/training/portal/learner/course")
@Validated
public class PortalLearnerCourseController {

    @Resource
    private LearnerCourseService learnerCourseService;

    @GetMapping("/my-list")
    @Operation(summary = "获得我的课程（按 待学习/学习中/已完成 分组，含汇总卡片数据）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<MyCoursesGroupRespVO> getMyCourses() {
        List<LearnerCourseCardRespVO> cards = learnerCourseService.getMyCourses(getLoginUserId());
        MyCoursesGroupRespVO result = new MyCoursesGroupRespVO();
        List<LearnerCourseCardRespVO> todo = new ArrayList<>();
        List<LearnerCourseCardRespVO> learning = new ArrayList<>();
        List<LearnerCourseCardRespVO> completed = new ArrayList<>();
        for (LearnerCourseCardRespVO card : cards) {
            int status = card.getStatus() == null ? LearnerTaskStatusEnum.TODO.getStatus() : card.getStatus();
            if (Objects.equals(status, LearnerTaskStatusEnum.COMPLETED.getStatus())) {
                completed.add(card);
            } else if (Objects.equals(status, LearnerTaskStatusEnum.LEARNING.getStatus())) {
                learning.add(card);
            } else {
                todo.add(card);
            }
        }
        result.setTodoCourses(todo);
        result.setLearningCourses(learning);
        result.setCompletedCourses(completed);
        result.setTotalCount(cards.size());
        result.setTodoCount(todo.size());
        result.setLearningCount(learning.size());
        result.setCompletedCount(completed.size());
        BigDecimal creditTotal = BigDecimal.ZERO;
        BigDecimal creditEarned = BigDecimal.ZERO;
        for (LearnerCourseCardRespVO card : cards) {
            creditTotal = creditTotal.add(card.getCreditTotal() == null ? BigDecimal.ZERO : card.getCreditTotal());
            creditEarned = creditEarned.add(card.getCreditEarned() == null ? BigDecimal.ZERO : card.getCreditEarned());
        }
        result.setCreditTotal(creditTotal.setScale(2, RoundingMode.HALF_UP));
        result.setCreditEarned(creditEarned.setScale(2, RoundingMode.HALF_UP));
        return success(result);
    }

    @GetMapping("/detail")
    @Operation(summary = "获得课程学习详情（含媒资逐项进度）")
    @Parameter(name = "id", description = "课程 ID", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<LearnerCourseDetailRespVO> getCourseDetail(@RequestParam("id") Long id) {
        return success(learnerCourseService.getCourseDetail(getLoginUserId(), id));
    }

    @PostMapping("/enroll-self")
    @Operation(summary = "自主选学课程（已指派/已选学则幂等）")
    @Parameter(name = "id", description = "课程 ID", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> enrollSelf(@RequestParam("id") Long id) {
        learnerCourseService.enrollSelf(getLoginUserId(), id);
        return success(true);
    }

}
