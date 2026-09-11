package cn.iocoder.txgy.module.training.controller.admin.plan;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassCourseRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassSaveReqVO;
import cn.iocoder.txgy.module.training.service.plan.ClassServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 培训班管理")
@RestController
@RequestMapping("/training/plan/class")
@Validated
public class PlanClassController {

    @Resource
    private ClassServiceImpl classService;

    @PostMapping("/create")
    @Operation(summary = "创建培训班（初始状态：草稿，创建时绑定课程清单-定课）")
    @PreAuthorize("@ss.hasPermission('training:plan:create')")
    public CommonResult<Long> createClass(@Valid @RequestBody ClassSaveReqVO createReqVO) {
        return success(classService.createClass(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改培训班（仅草稿状态可编辑；已有学员后课程清单锁定）")
    @PreAuthorize("@ss.hasPermission('training:plan:update')")
    public CommonResult<Boolean> updateClass(@Valid @RequestBody ClassSaveReqVO updateReqVO) {
        classService.updateClass(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除培训班（仅草稿状态可删除，连带清理绑定）")
    @Parameter(name = "id", description = "培训班 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:plan:delete')")
    public CommonResult<Boolean> deleteClass(@RequestParam("id") Long id) {
        classService.deleteClass(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "培训班分页列表")
    @PreAuthorize("@ss.hasPermission('training:plan:query')")
    public CommonResult<PageResult<ClassRespVO>> getClassPage(@Validated ClassPageReqVO pageReqVO) {
        return success(classService.getClassPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "培训班详情（含绑定课程 ID 列表）")
    @Parameter(name = "id", description = "培训班 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:plan:query')")
    public CommonResult<ClassRespVO> getClass(@RequestParam("id") Long id) {
        return success(classService.getClass(id));
    }

    @GetMapping("/course-list")
    @Operation(summary = "培训班课程清单（定课快照）")
    @Parameter(name = "classId", description = "培训班 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:plan:query')")
    public CommonResult<List<ClassCourseRespVO>> getClassCourseList(@RequestParam("classId") Long classId) {
        return success(classService.getClassCourseList(classId));
    }

    @PostMapping("/start")
    @Operation(summary = "开班（草稿 → 进行中，自动生成编码 TB{id}；需已定课并设置截止时间）")
    @Parameter(name = "id", description = "培训班 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:plan:update')")
    public CommonResult<Boolean> startClass(@RequestParam("id") Long id) {
        classService.startClass(id);
        return success(true);
    }

    @PostMapping("/finish")
    @Operation(summary = "结业（进行中 → 已完成）")
    @Parameter(name = "id", description = "培训班 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:plan:update')")
    public CommonResult<Boolean> finishClass(@RequestParam("id") Long id) {
        classService.finishClass(id);
        return success(true);
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消培训班（草稿/进行中 → 已取消）")
    @Parameter(name = "id", description = "培训班 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:plan:update')")
    public CommonResult<Boolean> cancelClass(@RequestParam("id") Long id) {
        classService.cancelClass(id);
        return success(true);
    }

}
