package cn.iocoder.txgy.module.bidding.controller.admin.file;

import cn.iocoder.txgy.module.bidding.controller.admin.file.vo.BiddingFileRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.file.BiddingFileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.file.BiddingFileMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.file.BiddingFileSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.file.BiddingFileVirusStatusEnum;
import cn.iocoder.txgy.module.bidding.service.file.BiddingFileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * **文件收敛契约测试（standalone，不依赖 Spring 容器/DB）**：依据
 * 《文件接口收敛方案-后端实施篇-2026-09-20》与《招投标模块开发规范》§5 三-5、红线 8，校验：
 * ① 读出口唯一：GET /bidding/file/download?id=（isAuthenticated），业务不再依赖 /infra/file/get；
 * ② 病毒统一口径：virusStatus=INFECTED 一律拒绝下发（1-060-101-021）；
 * ③ 登记表存"存储坐标"而非裸 url（configId+path），未登记/坐标缺失给出明确错误码；
 * ④ 错误码注册（101-020~023）。
 */
class BiddingFileConvergenceContractTest {

    private BiddingFileMapper biddingFileMapper;
    private BiddingFileServiceImpl service;

    @BeforeEach
    void setUp() {
        biddingFileMapper = mock(BiddingFileMapper.class);
        service = new BiddingFileServiceImpl();
        ReflectionTestUtils.setField(service, "biddingFileMapper", biddingFileMapper);
    }

    private BiddingFileDO file(String virusStatus, Long configId, String path) {
        BiddingFileDO f = new BiddingFileDO();
        f.setFileId(1024L);
        f.setConfigId(configId);
        f.setPath(path);
        f.setName("招标公告.pdf");
        f.setType("application/pdf");
        f.setSize(2048L);
        f.setVirusStatus(virusStatus);
        f.setSource(BiddingFileSourceEnum.UPLOAD.name());
        return f;
    }

    @Test
    @DisplayName("读出口：GET /bidding/file/download 存在且鉴权为 isAuthenticated（无需 infra:file:query）")
    void downloadEndpoint_existsAndAuthenticated() {
        Method m = Arrays.stream(BiddingFileController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals("download")).findFirst()
                .orElseThrow(() -> new AssertionError("控制器缺少 download 方法（文件读出口唯一通道）"));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, "download 缺少 @PreAuthorize");
        assertTrue(a.value().contains("isAuthenticated"),
                "download 应为 isAuthenticated（业务账号无 infra:file:query），实际：" + a.value());
        assertTrue(!a.value().contains("infra:file"), "download 不得依赖 infra 权限（权限倒挂修复点）");
    }

    @Test
    @DisplayName("病毒口径：INFECTED 一律拒绝下载（1-060-101-021）")
    void download_infectedRejected() {
        when(biddingFileMapper.selectByFileId(1024L)).thenReturn(file(BiddingFileVirusStatusEnum.INFECTED.name(), 4L, "bidding/x.pdf"));
        Exception ex = assertThrows(Exception.class, () -> service.download(1024L));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("病毒"), "应拒绝并提示病毒未通过，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("未登记 fileId → FILE_NOT_REGISTERED（不跨库猜 infra_file，红线 6）")
    void download_notRegistered() {
        when(biddingFileMapper.selectByFileId(9999L)).thenReturn(null);
        Exception ex = assertThrows(Exception.class, () -> service.getByFileId(9999L));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("未登记"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("存储坐标缺失（仅元数据）→ FILE_STORAGE_COORD_INVALID")
    void download_missingCoord() {
        when(biddingFileMapper.selectByFileId(1024L)).thenReturn(file(BiddingFileVirusStatusEnum.CLEAN.name(), null, null));
        Exception ex = assertThrows(Exception.class, () -> service.download(1024L));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("存储坐标"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("登记表存存储坐标（configId+path），不含可访问 url（红线 8）")
    void registry_storesCoordNotUrl() {
        Set<String> fields = new TreeSet<>();
        for (Field f : BiddingFileDO.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                fields.add(f.getName());
            }
        }
        assertTrue(fields.containsAll(Set.of("fileId", "configId", "path", "sha256", "size", "virusStatus", "source")),
                "登记表应含三件套 + 存储坐标 + 病毒态，实际：" + fields);
        assertTrue(!fields.contains("url"), "登记表**不得**存可访问 url（红线 8：url 为回显快照、即凭据），实际字段：" + fields);
    }

    @Test
    @DisplayName("_060_101_020~023 错误码已注册")
    void errorCodes_registered() {
        assertEquals(1_060_101_020, ErrorCodeConstants.FILE_NOT_REGISTERED.getCode());
        assertEquals(1_060_101_021, ErrorCodeConstants.FILE_VIRUS_NOT_CLEAN.getCode());
        assertEquals(1_060_101_022, ErrorCodeConstants.FILE_STORAGE_COORD_INVALID.getCode());
        assertEquals(1_060_101_023, ErrorCodeConstants.FILE_CONTENT_READ_FAILED.getCode());
    }

    @Test
    @DisplayName("上传响应仍回 fileId 与 url（上传后即时预览），读出口另走 download")
    void uploadResp_unchanged() {
        Set<String> fields = new TreeSet<>();
        for (Field f : BiddingFileRespVO.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                fields.add(f.getName());
            }
        }
        assertEquals(new TreeSet<>(Set.of("fileId", "url", "name", "type", "size", "sha256")), fields,
                "上传出参契约不变（避免前端返工）");
    }
}
