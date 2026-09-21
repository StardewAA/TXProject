package cn.iocoder.txgy.module.training.controller.admin.plan;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentAddReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentImportAllocateReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentImportAllocateRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentImportExcelVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentImportParseRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentImportRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentScopePreviewRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentScopeReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.StudentTaskDO;
import cn.iocoder.txgy.module.training.service.plan.ClassStudentScopeService;
import cn.iocoder.txgy.module.training.service.plan.ClassStudentServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 培训班学员管理")
@RestController
@RequestMapping("/training/plan/class/student")
@Validated
public class PlanClassStudentController {

    @Resource
    private ClassStudentServiceImpl classStudentService;
    @Resource
    private ClassStudentScopeService classStudentScopeService;

    @PostMapping("/add")
    @Operation(summary = "批量添加学员（自动生成「课程 × 学员」学习任务；已在班自动跳过）")
    @PreAuthorize("@ss.hasAnyPermissions('training:plan:student:add', 'training:plan:student')")
    public CommonResult<Integer> addStudents(@Valid @RequestBody ClassStudentAddReqVO reqVO) {
        return success(classStudentService.addStudents(reqVO));
    }

    @PostMapping("/scope-preview")
    @Operation(summary = "参与对象筛选：预览命中人员（不落库，用于加入前确认）")
    @PreAuthorize("@ss.hasAnyPermissions('training:plan:student:add', 'training:plan:student')")
    public CommonResult<ClassStudentScopePreviewRespVO> previewScope(@Valid @RequestBody ClassStudentScopeReqVO reqVO) {
        return success(classStudentScopeService.previewScope(reqVO));
    }

    @PostMapping("/add-by-scope")
    @Operation(summary = "按参与对象筛选条件批量加入学员（部门/岗位，自动生成学习任务；已在班自动跳过）")
    @PreAuthorize("@ss.hasAnyPermissions('training:plan:student:add', 'training:plan:student')")
    public CommonResult<Integer> addStudentsByScope(@Valid @RequestBody ClassStudentScopeReqVO reqVO) {
        return success(classStudentScopeService.addStudentsByScope(reqVO));
    }

    @DeleteMapping("/remove")
    @Operation(summary = "移出学员（保留历史记录，学习任务作废）")
    @Parameter(name = "classId", description = "培训班 ID", required = true, example = "1")
    @Parameter(name = "userId", description = "学员用户 ID", required = true, example = "3001")
    @PreAuthorize("@ss.hasPermission('training:plan:student:remove')")
    public CommonResult<Boolean> removeStudent(@RequestParam("classId") Long classId,
                                               @RequestParam("userId") Long userId,
                                               @RequestParam(value = "reason", required = false) String reason) {
        classStudentService.removeStudent(classId, userId, reason);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "培训班在班学员分页")
    @PreAuthorize("@ss.hasPermission('training:plan:query')")
    public CommonResult<PageResult<ClassStudentRespVO>> getStudentPage(@Validated ClassStudentPageReqVO pageReqVO) {
        return success(classStudentService.getStudentPage(pageReqVO));
    }

    @GetMapping("/task-list")
    @Operation(summary = "学员在该培训班下的学习任务列表（用于进度登记）")
    @Parameter(name = "classId", description = "培训班 ID", required = true, example = "1")
    @Parameter(name = "userId", description = "学员用户 ID", required = true, example = "3001")
    @PreAuthorize("@ss.hasAnyPermissions('training:plan:progress:register', 'training:plan:progress', 'training:plan:query')")
    public CommonResult<List<StudentTaskDO>> getStudentTaskList(@RequestParam("classId") Long classId,
                                                                @RequestParam("userId") Long userId) {
        return success(classStudentService.getStudentTaskList(classId, userId));
    }

    @GetMapping("/import-template")
    @Operation(summary = "下载学员导入模板（Excel：姓名 / 手机号）")
    public void getImportTemplate(HttpServletResponse response) throws IOException {
        ExcelUtils.write(response, "培训班学员导入模板.xls", "学员", ClassStudentImportExcelVO.class,
                Collections.emptyList());
    }

    @PostMapping("/import")
    @Operation(summary = "Excel 批量导入学员（按手机号匹配系统账号，自动生成学习任务）")
    @Parameter(name = "classId", description = "培训班 ID", required = true, example = "1")
    @Parameter(name = "autoCreateUser", description = "未匹配到系统账号时，是否自动创建学员账号（登录名=手机号）", example = "false")
    @Parameter(name = "file", description = "Excel 文件", required = true)
    @PreAuthorize("@ss.hasPermission('training:plan:student:import')")
    public CommonResult<ClassStudentImportRespVO> importStudent(@RequestParam("classId") Long classId,
                                                                @RequestParam(value = "autoCreateUser", required = false, defaultValue = "false") Boolean autoCreateUser,
                                                                @RequestParam("file") MultipartFile file) throws Exception {
        List<ClassStudentImportExcelVO> list = ExcelUtils.read(file, ClassStudentImportExcelVO.class);
        return success(classStudentService.importStudents(classId, list, autoCreateUser));
    }

    @PostMapping("/import-parse")
    @Operation(summary = "解析 Excel 学员名单（按手机号匹配系统账号，不落库，供人工指定培训班）")
    @Parameter(name = "file", description = "Excel 文件", required = true)
    @PreAuthorize("@ss.hasAnyPermissions('training:plan:student:import', 'training:plan:student:add', 'training:plan:student')")
    public CommonResult<ClassStudentImportParseRespVO> parseImportFile(@RequestParam("file") MultipartFile file)
            throws Exception {
        List<ClassStudentImportExcelVO> list = ExcelUtils.read(file, ClassStudentImportExcelVO.class);
        return success(classStudentService.parseImportRows(list));
    }

    @PostMapping("/import-allocate")
    @Operation(summary = "按人工指定的「学员 → 培训班」明细批量入班（自动生成学习任务；已在班自动跳过）")
    @PreAuthorize("@ss.hasAnyPermissions('training:plan:student:import', 'training:plan:student:add', 'training:plan:student')")
    public CommonResult<ClassStudentImportAllocateRespVO> importAndAllocate(
            @Valid @RequestBody ClassStudentImportAllocateReqVO reqVO) {
        return success(classStudentService.importAndAllocate(reqVO));
    }

}
