package cn.iocoder.txgy.module.infra.api.file;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.infra.api.file.dto.FileBindReqDTO;
import cn.iocoder.txgy.module.infra.api.file.dto.FileCreateReqDTO;
import cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO;
import cn.iocoder.txgy.module.infra.api.file.dto.FileOssPromoteRetryRespDTO;
import cn.iocoder.txgy.module.infra.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - 文件")
public interface FileApi {

    String PREFIX = ApiConstants.PREFIX + "/file";

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @return 文件路径
     */
    default String createFile(byte[] content) {
        return createFile(content, null, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @param name 文件名称，允许空
     * @return 文件路径
     */
    default String createFile(byte[] content, String name) {
        return createFile(content, name, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @param name 文件名称，允许空
     * @param directory 目录，允许空
     * @param type 文件的 MIME 类型，允许空
     * @return 文件路径
     */
    default String createFile(@NotEmpty(message = "文件内容不能为空") byte[] content,
                              String name, String directory, String type) {
        return createFile(new FileCreateReqDTO().setName(name).setDirectory(directory).setType(type).setContent(content)).getCheckedData();
    }

    @PostMapping(PREFIX + "/create")
    @Operation(summary = "保存文件，并返回文件的访问路径")
    CommonResult<String> createFile(@Valid @RequestBody FileCreateReqDTO createReqDTO);

    /**
     * 生成文件预签名地址，用于读取
     *
     * @param url 完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    @GetMapping(PREFIX + "/presigned-url")
    @Operation(summary = "生成文件预签名地址，用于读取")
    CommonResult<String> presignGetUrl(@NotEmpty(message = "URL 不能为空") @RequestParam("url") String url,
                                       @RequestParam(value = "expirationSeconds", required = false)
                                       Integer expirationSeconds);

    @GetMapping(PREFIX + "/metadata-by-url")
    @Operation(summary = "按访问地址查询文件元数据")
    CommonResult<FileMetadataRespDTO> getMetadataByUrl(
            @NotEmpty(message = "URL 不能为空") @RequestParam("url") String url);

    /**
     * 按访问地址读取文件内容，用于服务端二次加工（例如合同模板渲染）
     *
     * @param url 完整的文件访问地址
     * @return 文件内容
     */
    @GetMapping(PREFIX + "/content-by-url")
    @Operation(summary = "按访问地址读取文件内容")
    CommonResult<byte[]> getFileContentByUrl(
            @NotEmpty(message = "URL 不能为空") @RequestParam("url") String url);

    /**
     * 业务单据落库后，绑定文件归属（公开资源确认 / 私有资源归属与参与方）
     *
     * @param reqDTO 绑定请求
     * @return 实际更新的文件条数
     */
    @PostMapping(PREFIX + "/bind")
    @Operation(summary = "绑定文件业务归属")
    CommonResult<Integer> bindFile(@Valid @RequestBody FileBindReqDTO reqDTO);

    /**
     * 重试把「已确认公开但未进入 OSS」的文件补传到公开桶（可追踪、可安全重试）
     *
     * @param fileId 指定文件编号时只补传该文件；为空时批量补传全部 stranded 公开文件
     * @param limit  批量模式下的单次最大条数
     * @return 补传统计
     */
    @PostMapping(PREFIX + "/retry-oss-promotion")
    @Operation(summary = "重试 OSS 提升补传")
    CommonResult<FileOssPromoteRetryRespDTO> retryOssPromotion(
            @RequestParam(value = "fileId", required = false) Long fileId,
            @RequestParam(value = "limit", defaultValue = "200") int limit);

}
