package cn.iocoder.txgy.module.infra.framework.file.core.s3;

import cn.iocoder.txgy.module.infra.framework.file.core.client.s3.S3FileClient;
import cn.iocoder.txgy.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Temporary smoke test for the locally supplied OSS environment credentials. */
class OssCredentialSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_OSS_SMOKE", matches = "true")
    void uploadPublicReadAndDelete() throws Exception {
        S3FileClientConfig config = new S3FileClientConfig();
        config.setEndpoint("oss-cn-beijing.aliyuncs.com");
        config.setBucket("java-ai-comency");
        config.setDomain(null);
        config.setAccessKey("env:OSS_ACCESS_KEY");
        config.setAccessSecret("env:OSS_ACCESS_SECRET");
        config.setEnablePathStyleAccess(false);
        config.setEnablePublicAccess(true);
        config.setRegion("cn-beijing");

        S3FileClient client = new S3FileClient(0L, config);
        client.init();

        String path = "codex-smoke/" + UUID.randomUUID() + ".txt";
        byte[] expected = "temporary OSS connectivity check".getBytes(StandardCharsets.UTF_8);
        boolean uploaded = false;
        try {
            String url = client.upload(expected, path, "text/plain; charset=utf-8");
            uploaded = true;
            assertEquals("https://java-ai-comency.oss-cn-beijing.aliyuncs.com/" + path, url);

            HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertArrayEquals(expected, response.body());
        } finally {
            if (uploaded) {
                client.delete(path);
            }
        }
    }
}
