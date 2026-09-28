package cn.iocoder.txgy.module.hrm.controller.admin.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.funnel.HrmRecruitFunnelRespVO;
import cn.iocoder.txgy.module.hrm.service.recruit.funnel.HrmRecruitFunnelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 招聘漏斗看板：按职位统计 投递 → 筛选 → 面试 → Offer → 入职 的转化情况与平均入职耗时。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 招聘漏斗")
@RestController
@RequestMapping("/hrm/recruit/funnel")
@Validated
public class HrmRecruitFunnelController {

    @Resource
    private HrmRecruitFunnelService recruitFunnelService;

    @GetMapping("/list")
    @Operation(summary = "获得招聘漏斗（按职位）", description = "默认统计最近 6 个月，按投递人数倒序")
    @Parameter(name = "months", description = "统计最近几个月，默认 6", example = "6")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:funnel:query')")
    public CommonResult<List<HrmRecruitFunnelRespVO>> getFunnelList(
            @RequestParam(value = "months", required = false) Integer months) {
        return success(recruitFunnelService.getFunnelList(months));
    }

}
