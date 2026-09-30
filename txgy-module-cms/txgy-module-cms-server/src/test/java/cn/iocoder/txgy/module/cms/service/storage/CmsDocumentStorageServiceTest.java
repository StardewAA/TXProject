package cn.iocoder.txgy.module.cms.service.storage;

import cn.iocoder.txgy.module.cms.config.CmsStorageProperties;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CmsDocumentStorageServiceTest {

    @Test
    void localStoragePersistsOutsideMysqlAndCanReadBack() {
        // Windows 上部分安全软件会短暂占用新建文件，避免由 @TempDir 在测试结束时强制递归清理。
        Path temporaryDirectory = Path.of("target", "test-storage", UUID.randomUUID().toString());
        CmsStorageProperties properties = new CmsStorageProperties();
        properties.setMode("local");
        properties.setLocalRoot(temporaryDirectory.toString());
        var provider = new StaticListableBeanFactory().getBeanProvider(FileApi.class);
        CmsDocumentStorageService service = new CmsDocumentStorageService(properties, provider);
        byte[] content = "法规原件".getBytes(StandardCharsets.UTF_8);

        String uri = service.store(1L, "550e8400-e29b-41d4-a716-446655440000", "../法规?.txt", content);

        assertThat(uri).startsWith("local:").doesNotContain("..");
        assertThat(service.read(uri)).isEqualTo(content);
        assertThat(service.accessUrl(uri)).isNull();
    }

    @Test
    void legacyFilesPathReadsFromConfiguredStaticRoot() throws Exception {
        Path staticRoot = Path.of("target", "test-static-files", UUID.randomUUID().toString());
        Path policy = staticRoot.resolve("finance-policy").resolve("金融政策.pdf");
        Files.createDirectories(policy.getParent());
        byte[] content = "金融政策原件".getBytes(StandardCharsets.UTF_8);
        Files.write(policy, content);

        CmsStorageProperties properties = new CmsStorageProperties();
        properties.setStaticFilesRoot(staticRoot.toString());
        var provider = new StaticListableBeanFactory().getBeanProvider(FileApi.class);
        CmsDocumentStorageService service = new CmsDocumentStorageService(properties, provider);

        assertThat(service.read("/files/finance-policy/金融政策.pdf")).isEqualTo(content);
    }
}
