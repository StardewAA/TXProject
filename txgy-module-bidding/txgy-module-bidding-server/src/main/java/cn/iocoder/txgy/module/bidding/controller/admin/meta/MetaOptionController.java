package cn.iocoder.txgy.module.bidding.controller.admin.meta;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.OptionItemRespVO;
import cn.iocoder.txgy.module.bidding.enums.BiddingOptionTypeEnum;
import cn.iocoder.txgy.module.bidding.service.meta.BiddingOptionService;
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

/**
 * 统一候选选项接口——把各页面"手输主键"收口成一套接口。
 *
 * <p>规范出处：契约《24-统一候选接口-options-契约-2026-09-15》；用户口径 2026-09-15「统一接口」。
 * <p>与 {@link MetaController} 同属 {@code /bidding/meta} 家族（dicts=枚举、configs=阈值、options=候选），
 * 前端只需记住一个前缀：运行时元数据全部来自 {@code /bidding/meta/*}，页面里不许出现裸主键输入框。
 * <p>为什么路径归到 meta 而不是各业务域：候选值是**跨页面前置元数据**（填报资质要选企业主体、配证要选员工、
 * 挂接要选证照），散在各域会出现"同一份候选、两套参数与形状"的老问题；且权限需按"任一域页面权限即可读"放行，
 * 绑死到单域会让其它域下拉变空、逼前端回落到手输。
 */
@Tag(name = "管理后台 - 统一候选选项")
@RestController
@RequestMapping("/bidding/meta")
@Validated
public class MetaOptionController {

    @Resource
    private BiddingOptionService biddingOptionService;

    @GetMapping("/options")
    @Operation(summary = "统一候选选项（消灭前端手输主键）",
            description = "按 type 返回统一形状 {value,label,extra,disabled,status} 的候选列表；"
                    + "value 为雪花 ID 十进制字符串（前端原样回传，勿转 Number）；"
                    + "失效/离职对象仍返回但 disabled=true（前端置灰，不要过滤）。"
                    + "fileId 不在此接口：文件须走 POST /bidding/file/upload 取响应中的 fileId。")
    @Parameter(name = "type", required = true,
            description = "候选类型：SUBJECT=企业主体 / PERSONNEL=员工 / CERTIFICATE=证照 / "
                    + "QUALIFICATION=企业资质 / SAFETY_PERMIT=安全生产许可证 / PERSONNEL_CERT=人员证书")
    @Parameter(name = "keyword", description = "模糊关键字：按该类型的名称/编号列匹配（可空）")
    @Parameter(name = "limit", description = "返回上限：默认 20，最大 50（超出按 50）")
    // 权限口径与 /bidding/meta/dicts 一致：候选值是所有资质页面共用的前置元数据，
    // 绑死单域权限会让其它域的下拉取不到值而回落成手输主键（正是本接口要消灭的）。
    @PreAuthorize("@ss.hasAnyPermissions('bidding:certificate:query', 'bidding:qualification:query', "
            + "'bidding:personnel:profile:query', 'bidding:certificate:lifecycle:query', "
            + "'bidding:qualification:permit:query', 'bidding:subject:query')")
    public CommonResult<List<OptionItemRespVO>> options(
            @RequestParam("type") String type,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        BiddingOptionTypeEnum optionType = BiddingOptionTypeEnum.parse(type);
        if (optionType == null) {
            // 明确报错并回列可选项：静默返回空列表会让前端以为"这个类型就是没数据"
            return CommonResult.error(400, "候选类型不支持：" + type + "（可选：" + BiddingOptionTypeEnum.codes() + "）");
        }
        return CommonResult.success(biddingOptionService.listOptions(optionType, keyword, limit));
    }
}
