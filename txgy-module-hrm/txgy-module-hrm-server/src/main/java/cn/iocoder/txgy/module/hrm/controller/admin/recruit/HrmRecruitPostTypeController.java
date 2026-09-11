package cn.iocoder.txgy.module.hrm.controller.admin.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.type.HrmRecruitPostTypeRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.type.HrmRecruitPostTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.type.HrmRecruitPostTypeStatusReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostTypeDO;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 招聘职位类型")
@RestController
@RequestMapping("/hrm/recruit/post-type")
@Validated
public class HrmRecruitPostTypeController {

    @Resource
    private HrmRecruitPostTypeService recruitPostTypeService;

    @GetMapping("/list")
    @Operation(summary = "获得招聘职位类型列表")
    @Parameter(name = "status", description = "状态", example = "0")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:post-type:query') or @ss.hasPermission('hrm:recruit:post:query')")
    public CommonResult<List<HrmRecruitPostTypeRespVO>> getRecruitPostTypeList(@RequestParam(value = "status", required = false) Integer status) {
        List<HrmRecruitPostTypeDO> postTypes = recruitPostTypeService.getRecruitPostTypeList(status);
        return success(BeanUtils.toBean(postTypes, HrmRecruitPostTypeRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建招聘职位类型")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:post-type:create')")
    public CommonResult<Long> createRecruitPostType(@Valid @RequestBody HrmRecruitPostTypeSaveReqVO reqVO) {
        return success(recruitPostTypeService.createRecruitPostType(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新招聘职位类型")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:post-type:update')")
    public CommonResult<Boolean> updateRecruitPostType(@Valid @RequestBody HrmRecruitPostTypeSaveReqVO reqVO) {
        recruitPostTypeService.updateRecruitPostType(reqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "更新招聘职位类型状态")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:post-type:update')")
    public CommonResult<Boolean> updateRecruitPostTypeStatus(
            @Valid @RequestBody HrmRecruitPostTypeStatusReqVO reqVO) {
        recruitPostTypeService.updateRecruitPostTypeStatus(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除招聘职位类型")
    @Parameter(name = "id", description = "职位类型编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:post-type:delete')")
    public CommonResult<Boolean> deleteRecruitPostType(@RequestParam("id") Long id) {
        recruitPostTypeService.deleteRecruitPostType(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得招聘职位类型")
    @Parameter(name = "id", description = "职位类型编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:post-type:query')")
    public CommonResult<HrmRecruitPostTypeRespVO> getRecruitPostType(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(recruitPostTypeService.getRecruitPostType(id),
                HrmRecruitPostTypeRespVO.class));
    }

}
