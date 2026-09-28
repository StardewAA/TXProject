package cn.iocoder.txgy.module.bidding.controller.admin.contract;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.contract.ContractDO;
import cn.iocoder.txgy.module.bidding.service.contract.ContractService;
import io.swagger.v3.oas.annotations.Operation;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 工程合同（合同签署与项目完结）。
 *
 * <p>登记中标后订立的书面合同，登记时校验履约保证金 ≤ 合同价 10%（条例第57条）
 * 与签约 ≤ 中标通知书发出 30 日（招标投标法第46条）。
 */
@Tag(name = "管理后台 - 工程合同")
@RestController
@RequestMapping("/bidding/contract")
@Validated
public class ContractController {

    @Resource
    private ContractService contractService;

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:contract:save')")
    @Operation(summary = "登记合同（校验履约保证金 10% 与签约 30 日）")
    @Idempotent
    public CommonResult<Long> save(@Valid @RequestBody ContractSaveReqVO reqVO) {
        return success(contractService.save(reqVO));
    }

    @PostMapping("/archive")
    @PreAuthorize("@ss.hasPermission('bidding:contract:archive')")
    @Operation(summary = "合同归档（SIGNED→ARCHIVED 只读锁定，落保存到期=归档+15年，幂等）")
    @Idempotent
    public CommonResult<Boolean> archive(@RequestParam("id") Long id) {
        contractService.archive(id);
        return success(true);
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:contract:query')")
    @Operation(summary = "合同详情")
    public CommonResult<ContractRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(contractService.get(id), ContractRespVO.class));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:contract:query')")
    @Operation(summary = "合同台账分页")
    public CommonResult<PageResult<ContractRespVO>> page(@Valid ContractPageReqVO reqVO) {
        PageResult<ContractDO> page = contractService.page(reqVO);
        return success(BeanUtils.toBean(page, ContractRespVO.class));
    }
}
