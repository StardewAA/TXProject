package cn.iocoder.txgy.module.bidding.controller.admin.prequal;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalCommitteeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalCommitteeSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalCommitteeDO;
import cn.iocoder.txgy.module.bidding.service.prequal.PrequalCommitteeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 资格审查委员会（条例第18条：国有资金项目强制组建；审查前置校验）。
 *
 * <p>设计依据：《电子招标投标全流程说明书》§6.3 第 3 条；《实施条例》第18/37条。
 */
@Tag(name = "管理后台 - 资格审查委员会")
@RestController
@RequestMapping("/bidding/prequal/committee")
@Validated
public class PrequalCommitteeController {

    @Resource
    private PrequalCommitteeService prequalCommitteeService;

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:committee')")
    @Operation(summary = "保存资格审查委员会（整单覆盖；成员≥5人单数、专家≥2/3，条例第37条）")
    @Idempotent
    public CommonResult<Boolean> save(@Valid @RequestBody PrequalCommitteeSaveReqVO reqVO) {
        prequalCommitteeService.save(reqVO);
        return success(true);
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('bidding:prequal:query')")
    @Operation(summary = "资格审查委员会成员列表（按资格预审）")
    public CommonResult<List<PrequalCommitteeRespVO>> list(@RequestParam("prequalId") @Parameter(description = "资格预审ID") Long prequalId) {
        List<PrequalCommitteeDO> list = prequalCommitteeService.listByPrequal(prequalId);
        return success(BeanUtils.toBean(list, PrequalCommitteeRespVO.class));
    }
}
