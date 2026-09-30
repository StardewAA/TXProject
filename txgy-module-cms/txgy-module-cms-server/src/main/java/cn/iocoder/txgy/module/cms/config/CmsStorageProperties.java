package cn.iocoder.txgy.module.cms.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** CMS 原件与网页快照存储。local 用于单机开发，infra 复用平台 FileApi（可配置 MinIO/S3）。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "txgy.cms.storage")
public class CmsStorageProperties {

    /** local 或 infra。 */
    @NotBlank
    private String mode = "local";

    /** local 模式的数据根目录，使用独立目录，避免与构建产物混在一起。 */
    @NotBlank
    private String localRoot = "./data/cms-knowledge";

    /**
     * 历史批量导入文件目录。数据库中的 /files/... 地址从该目录读取，
     * 兼容早期由前端 Nginx 直接托管的金融政策 PDF。
     */
    @NotBlank
    private String staticFilesRoot = "./deploy/content-files";

    /** infra 模式下载地址的预签名有效时间。 */
    private int presignSeconds = 1800;
    /** Chrome/Edge 可执行文件；留空时自动发现常见安装位置。 */
    private String snapshotBrowser = "";
    private int snapshotTimeoutSeconds = 45;
    private int snapshotTokenMinutes = 30;
}
