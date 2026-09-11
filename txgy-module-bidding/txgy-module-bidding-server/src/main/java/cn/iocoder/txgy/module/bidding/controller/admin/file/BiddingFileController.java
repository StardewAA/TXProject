package cn.iocoder.txgy.module.bidding.controller.admin.file;

// [规范依据]《招投标模块开发规范》§4.2（Controller 位于 controller/admin/<域>，名 XxxController）
//   + §4.4/§4.5（映射方法带 @PreAuthorize；写方法带 @Idempotent 幂等）
//   + §5 三-5 文件存储口径（FileApi + file_id/sha256/size）。
// [业务目标] 为 bidding 全部业务表单提供**统一的上传入口**：响应体必带 file_id，
//   前端上传成功即可自动绑定到表单隐藏字段，彻底移除"让用户手填文件 ID"的退化交互。
// [为什么] 平台既有 `POST /infra/file/upload` 只回 URL（无 id），业务表却以 file_id 关联，
//   二者不匹配是本次缺陷的根因；在 bidding 侧补一个语义完整的上传接口，
//   比修改 infra 公共接口（影响全平台调用方）风险更低，也不改变既有接口契约。

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.controller.admin.file.vo.BiddingFileRespVO;
import cn.iocoder.txgy.module.bidding.service.file.BiddingFileService;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - bidding 文件")
@RestController
@RequestMapping("/bidding/file")
@Validated
public class BiddingFileController {

    @Resource
    private BiddingFileService biddingFileService;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    // 幂等键按「文件名 + 大小」取：默认解析器会忽略 MultipartFile 参数（StrUtils.joinMethodArgs 排除 org.springframework.web.*），
    // 导致同目录下 1 秒内的**不同**文件被误判为重复请求（批量选文件时必现）。按文件指纹取键后，
    // 只有"同一文件在同一秒内重复提交"才会被拦（这才是真正需要拦的重复提交）。
    @Idempotent(keyResolver = cn.iocoder.txgy.framework.idempotent.core.keyresolver.impl.ExpressionIdempotentKeyResolver.class,
            keyArg = "#file == null ? 'empty' : (#file.originalFilename + ':' + #file.size)")
    @Operation(summary = "上传文件并返回 file_id", description = "响应体必带全局唯一 fileId（业务表落库关联键）；"
            + "文件服务未回传 file_id 时返回明确错误「文件注册失败，请重试」，不返回只有 URL 的成功响应")
    @Parameter(name = "file", description = "文件附件", required = true,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary"))
    public CommonResult<BiddingFileRespVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "directory", required = false) String directory) {
        return success(biddingFileService.upload(file, directory));
    }

    @PostMapping("/register-by-url")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "按已上传地址补登记 file_id", description = "历史「只有 URL 没有 file_id」数据的修复通道："
            + "传入平台文件服务返回的访问地址，回查元数据补出 fileId；查不到则报「文件地址无效」")
    public CommonResult<BiddingFileRespVO> registerByUrl(
            @RequestParam("url") @NotEmpty(message = "文件访问地址不能为空") String url) {
        return success(biddingFileService.registerByUrl(url));
    }

}
