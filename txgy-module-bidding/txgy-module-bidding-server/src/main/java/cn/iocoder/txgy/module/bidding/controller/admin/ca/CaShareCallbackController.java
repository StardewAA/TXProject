package cn.iocoder.txgy.module.bidding.controller.admin.ca;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareCallbackRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareLaunchParamVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareLaunchReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareSealCallbackReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareSealClientCallbackReqVO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.bidding.service.ca.CaShareCallbackService;
import cn.iocoder.txgy.module.bidding.service.ca.CaShareLaunchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * **官方 CA 互认签章回调接口**（对接官方签章验签工具，字段与响应格式逐字对齐官方规范）。
 *
 * <p>依据：《移动数字证书(CA)互认技术标准 签章验签工具接入指南 交易系统/交易工具侧 20241212》
 * §4.2.1（客户端回调）/ §4.2.2（H5 组件回调）——见 docs/接口互认/07-签章验签工具接入指南-交易系统侧.md。
 *
 * <p><b>为什么单独一套 Controller 而不复用 {@code /bidding/ca/seal/callback}</b>：
 * 现有 {@code /callback} 是本模块自定义口径（businessCode/documentId/signature/certSn/hash），
 * 而**官方回调是固定结构**（顶层 tradingSystemCode + callbackRequestType + document[]，
 * 或 action + data.signatureInfo.document[]），且**必须返回官方响应结构**
 * （{@code {code,success,data,msg,errorCode}}）——对方按自己的结构解析，返回 CommonResult 会被判为失败。
 * 两者口径不同，混用即对接失败，故并列提供、各自保持纯正。
 *
 * <p><b>鉴权（两个注解缺一不可）</b>：官方组件/客户端按配置的回调地址 POST，**不带本模块登录态、也不带租户头**，故：
 * <ul>
 *   <li>{@code @PermitAll} —— 框架扫描该注解生成免登录 URL 列表（TxgyWebSecurityConfigurerAdapter）；
 *       <b>缺它 Security 在过滤器层直接 401</b>，后面的注解都无机会生效（2026-09-21 实测确认）；</li>
 *   <li>{@code @TenantIgnore} —— 无租户头时租户拦截器会拦为「请求的租户标识未传递」(400)。</li>
 * </ul>
 * 安全边界由"回调地址不外泄 + 官方机器指纹字段留痕"承担；若需更强校验，可约定共享密钥并校验。
 * <p><b>落库租户口径</b>：忽略租户上下文后，回写台账须**按待签文件反查其所属租户**再限定匹配，
 * 不能仅按文件哈希跨租户匹配（相同内容文件在不同租户下哈希相同，会误改他租户台账）。
 */
@Tag(name = "管理后台 - CA 互认官方签章回调")
@RestController
@RequestMapping("/bidding/ca/share")
@Validated
@Slf4j
public class CaShareCallbackController {

    @Resource
    private CaShareCallbackService caShareCallbackService;
    @Resource
    private CaShareLaunchService caShareLaunchService;

    /**
     * 签发官方调起参数（契约 R3，2026-09-21 前端请求）。
     *
     * <p><b>为什么要这个端点</b>：§5.3.1 的调起参数含 {@code clientSecret}/{@code accessKeySecret}（平台密钥）。
     * 没有签发端点，前端只能把密钥硬编码进前端产物 → 任何人 F12 即可取走并**代本平台发起签章请求**。
     * 故参数由后端拼装、明文密钥只走服务端→官方工具这条链路。
     *
     * <p><b>鉴权</b>：本端点给**前端**用（不是官方回调），故走正常登录态，**不加** @PermitAll/@TenantIgnore。
     */
    @PostMapping("/launch-param")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "签发官方 CA 调起参数（R3；含平台密钥，服务端拼装）",
            description = "返回官方 §5.3.1 结构；前端拿到后交给官方检测安装工具（URL 协议唤起 / H5 组件）。"
                    + "密钥不下发到前端源码，仅随本次响应一次性下发")
    public CommonResult<CaShareLaunchParamVO> issueLaunchParam(@RequestBody @Valid CaShareLaunchReqVO reqVO) {
        return CommonResult.success(caShareLaunchService.issueLaunchParam(
                reqVO.getAwaitingSignatureFilePath(), reqVO.getAwaitingSignatureFileName(),
                reqVO.getSignatureFileSavePath(), reqVO.getSignatureFileName(), reqVO.getFileType()));
    }

    @PostMapping("/seal/callback")
    @PermitAll // 框架扫描 @PermitAll 生成免登录 URL 列表；缺此注解 Security 在过滤器层直接 401（实测确认）
    @TenantIgnore // 官方组件回调不带本系统登录态与租户头，必须忽略租户上下文（否则先被租户拦截器拦为 400）
    @Idempotent // 同一回调内容重复提交拦截（默认 resolver=方法名+参数；防官方组件重试导致重复回写台账）
    @Operation(summary = "官方 H5 签章组件回调（§4.2.2：action + data.signatureInfo.document[]）",
            description = "字段与响应格式逐字对齐官方规范；按 awaitingSignatureFileId/transactionId 回写签章结果")
    public CaShareCallbackRespVO h5Callback(@RequestBody CaShareSealCallbackReqVO reqVO) {
        // 独立 try/catch：官方回调失败也要返回**官方结构**的失败响应（而不是抛异常给出 500 页面），
        // 否则对方无法识别失败原因，且可能反复重试。
        try {
            caShareCallbackService.handleH5Callback(reqVO);
            return CaShareCallbackRespVO.ok();
        } catch (Exception e) {
            log.warn("[CaShareCallback][H5] 处理失败 action={} err={}", reqVO == null ? null : reqVO.getAction(), e.getMessage(), e);
            return CaShareCallbackRespVO.fail(e.getMessage());
        }
    }

    @PostMapping("/seal/client-callback")
    @PermitAll // 同上：官方客户端回调无登录态，必须免登录
    @TenantIgnore // 同 H5 回调：官方客户端直接 POST 回调地址，无登录态与租户头
    @Idempotent // 同一回调内容重复提交拦截（默认 resolver=方法名+参数；防官方客户端重试导致重复回写台账）
    @Operation(summary = "官方客户端签章工具回调（§4.2.1：顶层 tradingSystemCode + document[]）",
            description = "字段与响应格式逐字对齐官方规范；按待签/签后文件 Hash 回写签章结果")
    public CaShareCallbackRespVO clientCallback(@RequestBody CaShareSealClientCallbackReqVO reqVO) {
        try {
            caShareCallbackService.handleClientCallback(reqVO);
            return CaShareCallbackRespVO.ok();
        } catch (Exception e) {
            log.warn("[CaShareCallback][Client] 处理失败 type={} err={}",
                    reqVO == null ? null : reqVO.getCallbackRequestType(), e.getMessage(), e);
            return CaShareCallbackRespVO.fail(e.getMessage());
        }
    }
}
