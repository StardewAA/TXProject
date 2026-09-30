package cn.iocoder.txgy.module.cms.service.storage;

import cn.iocoder.txgy.module.cms.config.CmsStorageProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CmsUrlSnapshotServiceTest {

    @Test
    void stageRendersPdfAndTokenCanOnlyBeConsumedOnce() {
        Path chrome = Path.of("C:/Program Files/Google/Chrome/Application/chrome.exe");
        if (!Files.isRegularFile(chrome)) {
            chrome = Path.of("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe");
        }
        assumeTrue(Files.isRegularFile(chrome), "运行环境未安装 Chrome/Edge，跳过真实 PDF 渲染测试");
        CmsStorageProperties properties = new CmsStorageProperties();
        properties.setSnapshotBrowser(chrome.toString());
        properties.setSnapshotTimeoutSeconds(30);
        CmsUrlSnapshotService service = new CmsUrlSnapshotService(properties);

        String token = service.stage(1L, 9L, "安全生产法", "https://example.gov.cn/law/1",
                "第一条 为了加强安全生产工作。\n第二条 本法适用于生产经营活动。");
        CmsUrlSnapshotService.Snapshot snapshot = service.consume(1L, 9L, token,
                "https://example.gov.cn/law/1");

        assertThat(snapshot.filename()).endsWith(".pdf");
        assertThat(new String(snapshot.content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThatThrownBy(() -> service.consume(1L, 9L, token, "https://example.gov.cn/law/1"));
    }
}
