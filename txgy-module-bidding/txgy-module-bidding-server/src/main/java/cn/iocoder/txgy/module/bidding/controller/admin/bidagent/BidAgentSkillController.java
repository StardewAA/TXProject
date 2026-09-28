package cn.iocoder.txgy.module.bidding.controller.admin.bidagent;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentSkillVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentSkillDO;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentSkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 标书智能体技能")
@RestController
@RequestMapping("/bidding/bid-agent/skill")
@Validated
public class BidAgentSkillController {

    @Resource
    private BidAgentSkillService skillService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:skill:query')")
    @Operation(summary = "技能分页")
    public CommonResult<PageResult<BidAgentSkillVO.RespVO>> page(@Valid BidAgentSkillVO.PageReqVO reqVO) {
        return success(skillService.page(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:skill:query')")
    @Operation(summary = "技能详情")
    public CommonResult<BidAgentSkillVO.RespVO> get(@RequestParam("id") Long id) {
        return success(skillService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:skill:create') or @ss.hasPermission('bidding:bid-agent:skill:update')")
    @Idempotent
    @Operation(summary = "技能新增/修改")
    public CommonResult<Long> save(@Valid @RequestBody BidAgentSkillVO.SaveReqVO reqVO) {
        return success(skillService.save(reqVO));
    }

    @PostMapping("/delete")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:skill:delete')")
    @Idempotent
    @Operation(summary = "技能删除")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        skillService.delete(id);
        return success(true);
    }

    @GetMapping("/profile-list")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:skill:query')")
    @Operation(summary = "智能体已挂载技能（为空时按任务类型自动挂载推荐技能）")
    public CommonResult<List<Map<String, Object>>> profileList(@RequestParam("profileId") Long profileId,
                                                              @RequestParam(value = "taskType", required = false) String taskType) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BidAgentSkillDO skill : skillService.listByProfile(profileId, taskType, true)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", skill.getId());
            item.put("code", skill.getCode());
            item.put("name", skill.getName());
            item.put("description", skill.getDescription());
            item.put("applicableTaskTypes", skill.getApplicableTaskTypes());
            item.put("autoAttach", skill.getAutoAttach());
            result.add(item);
        }
        return success(result);
    }

    @PostMapping("/profile-save")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:skill:update')")
    @Idempotent
    @Operation(summary = "设置智能体挂载的技能（覆盖式）")
    public CommonResult<Boolean> profileSave(@Valid @RequestBody BidAgentSkillVO.ProfileSkillReqVO reqVO) {
        skillService.setProfileSkills(reqVO.getProfileId(), reqVO.getSkillIds());
        return success(true);
    }
}
