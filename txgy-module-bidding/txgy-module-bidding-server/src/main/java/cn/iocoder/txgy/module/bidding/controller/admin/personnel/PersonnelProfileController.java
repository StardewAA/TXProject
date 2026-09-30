package cn.iocoder.txgy.module.bidding.controller.admin.personnel;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelOverviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfilePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfileRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfileSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfileStatusChangeReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.service.personnel.PersonnelProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工档案管理接口（人员主数据：建档 / 检索 / 状态流转 / 资质全景）。
 *
 * <p>规范出处：用户业务口径"建立员工资质管理模块，实现人员证书的集中与动态管理"；
 * 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（人员要素）、表 15-6（只追加结论范式）；
 * 《招投标模块开发规范》§4.2（Controller 位于 controller/admin/&lt;域&gt;）、§4.4（RESTful + CommonResult/PageResult）、
 * §4.5（写接口 @Validated + @Idempotent + 乐观锁）、§4.8（L4 出参只回掩码）、§4.7（权限串 bidding:personnel:xxx）。
 * <p>为什么这样写：人员身份属 L4 高敏——明文只从建档入参进来一次，出参只回掩码；有效期变更与状态流转
 * 各走独立接口，避免"顺手改状态"绕过留痕。
 * <p>业务目标：员工信息一处维护，作为证书/培训/社保/劳动合同的挂载点与检索入口。
 */
@Tag(name = "管理后台 - 员工档案")
@RestController
@RequestMapping("/bidding/personnel-profile")
@Validated
public class PersonnelProfileController {

    @Resource
    private PersonnelProfileService personnelProfileService;

    @PostMapping("/create")
    @Operation(summary = "员工建档（一人一档：身份证盲索引判重）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:profile:create')")
    @Idempotent
    public CommonResult<Long> createProfile(@Valid @RequestBody PersonnelProfileSaveReqVO reqVO) {
        return CommonResult.success(personnelProfileService.createProfile(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新员工档案（乐观锁）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:profile:update')")
    @Idempotent
    public CommonResult<Boolean> updateProfile(@Valid @RequestBody PersonnelProfileSaveReqVO reqVO) {
        personnelProfileService.updateProfile(reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/change-status")
    @Operation(summary = "员工在职状态流转（离职/停职/复职）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:profile:update')")
    @Idempotent
    public CommonResult<Boolean> changeStatus(@Valid @RequestBody PersonnelProfileStatusChangeReqVO reqVO) {
        personnelProfileService.changeStatus(reqVO);
        return CommonResult.success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得员工档案")
    @Parameter(name = "id", description = "员工档案主键", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:profile:query')")
    public CommonResult<PersonnelProfileDO> getProfile(@RequestParam("id") Long id) {
        return CommonResult.success(personnelProfileService.getProfile(id));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询员工档案（支持按持有证书类型反查）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:profile:query')")
    public CommonResult<PageResult<PersonnelProfileDO>> pageProfile(@Valid PersonnelProfilePageReqVO reqVO) {
        return CommonResult.success(personnelProfileService.pageProfile(reqVO));
    }

    @GetMapping("/overview")
    @Operation(summary = "员工资质全景（档案+证书+社保+合同+培训学时+风险提示）",
            description = "主参数名 personId；同时兼容 id（契约 e2e P3-3：前端曾传 id 得到"
                    + "「请求参数缺失:personId」。二者传其一即可，personId 优先）")
    @Parameter(name = "personId", description = "员工档案主键（与 id 二选一，本参数优先）", example = "1024")
    @Parameter(name = "id", description = "员工档案主键（兼容别名，等价于 personId）", example = "1024")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:profile:query')")
    public CommonResult<PersonnelOverviewRespVO> getOverview(
            @RequestParam(value = "personId", required = false) Long personId,
            @RequestParam(value = "id", required = false) Long id) {
        // 契约 e2e P3-3：统一主参数名为 personId，同时兼容前端已发出的 id。
        // 为什么兼容而非直接改前端：该端点是"读一个人"的语义，两个名字都无歧义；
        // 后端兼容是一行成本，而前端改完还要发版（且历史调用方可能不止一处）。
        Long resolved = personId != null ? personId : id;
        if (resolved == null) {
            throw cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST);
        }
        return CommonResult.success(personnelProfileService.getOverview(resolved));
    }
}
