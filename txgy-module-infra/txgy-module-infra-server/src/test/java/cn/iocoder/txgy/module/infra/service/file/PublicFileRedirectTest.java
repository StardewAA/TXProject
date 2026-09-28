package cn.iocoder.txgy.module.infra.service.file;

import cn.iocoder.txgy.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.txgy.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.txgy.module.infra.framework.file.core.client.s3.S3FileClient;
import cn.iocoder.txgy.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicFileRedirectTest {
    @Test void publicHttpsUrlRequiresExplicitPublicConfiguration() {
        var config = new S3FileClientConfig();
        config.setDomain("https://images.example");
        var client = new S3FileClient(36L, config);
        assertNull(client.publicReadUrl("a.png"));
        config.setEnablePublicAccess(false);
        assertNull(client.publicReadUrl("a.png"));
        config.setEnablePublicAccess(true);
        assertEquals("https://images.example/a.png", client.publicReadUrl("a.png"));
        config.setDomain("http://images.example");
        assertNull(client.publicReadUrl("a.png"));
    }

    @Test void publicDefaultDomainWorksWithoutCredentialsOrSdkInit() {
        var config = new S3FileClientConfig();
        config.setEnablePublicAccess(true);
        config.setEndpoint("oss-cn-beijing.aliyuncs.com");
        config.setBucket("demo-bucket");
        var client = new S3FileClient(36L, config);
        assertEquals("https://demo-bucket.oss-cn-beijing.aliyuncs.com/a.png", client.publicReadUrl("a.png"));
        assertNull(config.getDomain()); // No shared configuration mutation or SDK initialization.
        config.setEndpoint("http://storage.example");
        assertNull(client.publicReadUrl("a.png"));
        config.setEnablePublicAccess(false);
        assertNull(client.publicReadUrl("a.png"));
    }

    @Test void oldGatewayUrlRedirectsOnlyAfterPublicAclAndNeverFetchesOssThroughSdk() throws Exception {
        var service = new FileServiceImpl();
        var mapper = mock(FileMapper.class);
        var configs = mock(FileConfigService.class);
        var config = new S3FileClientConfig();
        config.setDomain("https://images.example");
        config.setEnablePublicAccess(true);
        ReflectionTestUtils.setField(service, "fileMapper", mapper);
        ReflectionTestUtils.setField(service, "fileConfigService", configs);
        ReflectionTestUtils.setField(service, "publicOssConfigId", 36L);
        var file = new FileDO().setConfigId(36L).setPath("a.png").setAccessType(1).setPromoteStatus(2);
        when(mapper.selectFirstByPath("a.png")).thenReturn(file);
        when(configs.getFileConfig(36L)).thenReturn(new cn.iocoder.txgy.module.infra.dal.dataobject.file.FileConfigDO().setConfig(config));
        var request = new MockHttpServletRequest("GET", "/app-api/infra/file/4/get/a.png");
        var response = new MockHttpServletResponse();
        service.handleReadRequest(request, response, 4L, false);
        assertEquals(302, response.getStatus());
        assertEquals("https://images.example/a.png", response.getRedirectedUrl());
        verify(configs, never()).getFileClient(anyLong());
        file.setAccessType(2);
        var denied = new MockHttpServletResponse();
        service.handleReadRequest(request, denied, 4L, false);
        assertEquals(401, denied.getStatus());
        assertNull(denied.getRedirectedUrl());
        verify(configs, times(1)).getFileConfig(36L);
    }
}
