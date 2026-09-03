package cn.iocoder.txgy.module.hrm.controller.admin.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.*;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;

@Tag(name = "管理后台 - HRM 招聘应聘")
@RestController
@RequestMapping("/hrm/recruit/application")
@Validated
public class HrmRecruitApplicationController {
    @Resource
    private HrmRecruitApplicationService applicationService;

    @PostMapping("/create")
    @Operation(summary = "创建招聘应聘")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:application:create')")
    public CommonResult<Long> create(@Valid @RequestBody HrmRecruitApplicationCreateReqVO reqVO) {
        return success(applicationService.createApplication(reqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "获得招聘应聘分页")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:application:query')")
    public CommonResult<PageResult<HrmRecruitApplicationRespVO>> getPage(HrmRecruitApplicationPageReqVO reqVO) {
        PageResult<HrmRecruitApplicationDO> page = applicationService.getApplicationPage(reqVO);
        return success(new PageResult<>(buildList(page.getList()), page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得招聘应聘详情")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:application:query')")
    public CommonResult<HrmRecruitApplicationRespVO> get(@RequestParam("id") Long id) {
        HrmRecruitApplicationDO application = applicationService.getApplication(id);
        return success(application == null ? null : buildList(List.of(application)).get(0));
    }

    @PutMapping("/start-screening")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:application:screen')")
    public CommonResult<Boolean> startScreening(@Valid @RequestBody HrmRecruitApplicationStatusReqVO reqVO) {
        applicationService.startScreening(reqVO); return success(true);
    }

    @PutMapping("/eliminate")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:application:eliminate')")
    public CommonResult<Boolean> eliminate(@Valid @RequestBody HrmRecruitApplicationStatusReqVO reqVO) {
        applicationService.eliminate(reqVO); return success(true);
    }

    @PutMapping("/enter-interview")
    @Operation(summary = "进入面试")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:application:interview')")
    public CommonResult<Boolean> enterInterview(@Valid @RequestBody HrmRecruitApplicationStatusReqVO reqVO) {
        applicationService.enterInterview(reqVO); return success(true);
    }

    @PutMapping("/withdraw")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:application:withdraw')")
    public CommonResult<Boolean> withdraw(@Valid @RequestBody HrmRecruitApplicationStatusReqVO reqVO) {
        applicationService.withdraw(reqVO); return success(true);
    }

    private List<HrmRecruitApplicationRespVO> buildList(List<HrmRecruitApplicationDO> list) {
        return convertList(list, item -> BeanUtils.toBean(item, HrmRecruitApplicationRespVO.class,
                vo -> vo.setStatusName(HrmRecruitApplicationStatusEnum.valueOfStatus(vo.getStatus()) == null ? null
                        : HrmRecruitApplicationStatusEnum.valueOfStatus(vo.getStatus()).getName())));
    }
}
