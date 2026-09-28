package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CertFileVersionRespVO} 的**契约字段集**守卫。
 *
 * <p>规范出处：《电子证照模块-前端接口契约-v1.0》§3.2 明确列出的 13 个字段。
 *
 * <p>为什么这样写（设计遵循）：该端点原先把 {@code CertFileVersionDO} 直接外发，导致
 * ① 契约里写的 {@code sha256} 与实现的 {@code fileHash} 对不上（前端取到 undefined）；
 * ② {@code tenantId}/{@code deleted}/{@code creator} 等内部列被顺带外发。
 * 这类缺陷**编译通过、单测通过、文档还写着"已实现"**，只有把"外发的字段集合"当成契约来断言才会暴露。
 * 因此这里不测"能取到值"，而是把 **JSON 里真实出现的字段名集合**钉死：多一个少一个都失败。
 *
 * <p>业务目标：保证前端按契约 §3.2 取字段时**永远取得到**，且后端不会悄悄外发内部列。
 */
class CertFileVersionRespVoContractTest {

    /**
     * 契约 §3.2 列出的字段（唯一真相：改契约才改这里）。
     *
     * <p>演进记录（2026-09-21 对齐，此前本清单已过期导致用例假红）：
     * <ul>
     *   <li>F2 增 {@code voided/voidReason/voidedAt}（作废留痕展示）；</li>
     *   <li>字典治理（681c7907e）增 {@code fileCategoryName}（中文名，避免前端自行映射枚举）
     *       与 {@code downloadable}/{@code previewUrl}（受控预览出口，前端据此禁用按钮）；</li>
     *   <li>病毒字段统一口径：{@code virusScanStatus} → <b>{@code virusStatus}</b>
     *       （与 bidding_file 台账同名，避免同一概念两个字段名）。</li>
     * </ul>
     */
    private static final Set<String> CONTRACT_FIELDS = new TreeSet<>(List.of(
            "id", "certId", "fileCategory", "fileCategoryName", "fileName", "fileId",
            "fileFormat", "fileSize", "version", "isCurrent", "fileHash", "virusStatus",
            "downloadable", "previewUrl", "copyExpiringTime",
            "voided", "voidReason", "voidedAt", "createTime"));

    @Test
    @DisplayName("序列化字段集 == 契约 §3.2 字段（不多不少，内部列不外发）")
    void jsonFields_matchContract_exactly() throws Exception {
        CertFileVersionDO doObj = new CertFileVersionDO();
        doObj.setId(1L);
        doObj.setCertId(2L);
        doObj.setFileCategory("ORIGINAL");
        doObj.setFileName("a.pdf");
        doObj.setFileId(3L);
        doObj.setFileFormat("pdf");
        doObj.setFileSize(100L);
        doObj.setVersion(1);
        doObj.setIsCurrent(Boolean.TRUE);
        doObj.setVirusStatus("CLEAN");
        doObj.setFileHash("abc123");
        doObj.setCopyExpiringTime(LocalDateTime.of(2027, 1, 1, 0, 0));
        doObj.setVoided(Boolean.FALSE);
        // 内部列：必须**不**出现在 JSON 里（用 DO 上真实存在的字段验证映射是白名单而非反射拷贝）
        doObj.setCopyCreator("张三");
        doObj.setCopyCause("遗失补办");
        doObj.setRemark("内部备注");
        doObj.setTenantId(1L);

        // 注意：必须注册 JSR-310 模块，否则 LocalDateTime 字段会直接抛 InvalidDefinitionException，
        // 那样这条用例守的就不是"字段集"而是"序列化能不能过"（假绿/假红都发生过）。
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(CertFileVersionRespVO.from(doObj));
        Set<String> actual = new TreeSet<>();
        mapper.readTree(json).fieldNames().forEachRemaining(actual::add);

        assertEquals(CONTRACT_FIELDS, actual, "外发字段集必须与契约 §3.2 完全一致：多了是外发内部列，少了是前端拿不到值");
        assertTrue(json.contains("\"fileHash\""), "指纹字段必须叫 fileHash（契约 §3.2 写的 sha256 是笔误，见通知文件 §4.1）");
        assertTrue(!json.contains("copyCreator") && !json.contains("tenantId"), "内部列不得外发");
    }

    @Test
    @DisplayName("null 入参返回 null；空列表返回空列表（不抛异常）")
    void nullSafety() {
        assertNull(CertFileVersionRespVO.from(null));
        assertTrue(CertFileVersionRespVO.fromList(null).isEmpty());
    }

}
