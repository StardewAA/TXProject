package cn.iocoder.txgy.module.mes.service.qc.iqc;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.qc.iqc.MesQcIqcDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.qc.template.MesQcTemplateItemDO;
import cn.iocoder.txgy.module.mes.dal.mysql.qc.iqc.MesQcIqcMapper;
import cn.iocoder.txgy.module.mes.enums.MesBizTypeConstants;
import cn.iocoder.txgy.module.mes.enums.qc.MesQcStatusEnum;
import cn.iocoder.txgy.module.mes.service.md.autocode.MesMdAutoCodeRecordService;
import cn.iocoder.txgy.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.txgy.module.mes.service.md.vendor.MesMdVendorService;
import cn.iocoder.txgy.module.mes.service.qc.defectrecord.MesQcDefectRecordService;
import cn.iocoder.txgy.module.mes.service.qc.template.MesQcTemplateItemService;
import cn.iocoder.txgy.module.mes.service.wm.arrivalnotice.MesWmArrivalNoticeService;
import cn.iocoder.txgy.module.mes.service.wm.outsourcereceipt.MesWmOutsourceReceiptService;
import cn.iocoder.txgy.module.mes.service.qc.indicatorresult.MesQcIndicatorResultService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;

import java.math.BigDecimal;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link MesQcIqcServiceImpl} 的单元测试
 *
 * @author OPENLAB BS
 */
@Import(MesQcIqcServiceImpl.class)
public class MesQcIqcServiceImplTest extends BaseDbUnitTest {

    @Resource
    private MesQcIqcServiceImpl iqcService;

    @Resource
    private MesQcIqcMapper iqcMapper;

    @MockitoBean
    private MesWmArrivalNoticeService arrivalNoticeService;
    @MockitoBean
    private MesWmOutsourceReceiptService outsourceReceiptService;
    @MockitoBean
    private MesQcIqcLineService iqcLineService;
    @MockitoBean
    private MesQcDefectRecordService defectRecordService;
    @MockitoBean
    private MesMdVendorService vendorService;
    @MockitoBean
    private MesMdItemService itemService;
    @MockitoBean
    private MesQcTemplateItemService templateItemService;
    @MockitoBean
    private AdminUserApi adminUserApi;
    @MockitoBean
    private MesQcIndicatorResultService indicatorResultService;
    @MockitoBean
    private MesMdAutoCodeRecordService autoCodeRecordService;

    @Test
    public void testGenerateIqcDraft_success() {
        // mock：模板匹配、物料名称、自动编码
        Long templateId = randomLongId();
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder().templateId(templateId).build();
        when(templateItemService.getRequiredTemplateByItemIdAndType(anyLong(), anyInt())).thenReturn(templateItem);
        MesMdItemDO item = MesMdItemDO.builder().name("钢管-01").build();
        when(itemService.getItem(anyLong())).thenReturn(item);
        when(autoCodeRecordService.generateAutoCode(anyString())).thenReturn("IQC-AUTO-001");

        Long sourceDocId = randomLongId();
        Long sourceLineId = randomLongId();

        // 调用
        Long iqcId = iqcService.generateIqcDraft(MesBizTypeConstants.WM_ARRIVAL_NOTICE, sourceDocId, sourceLineId,
                "AN-001", 10L, 20L, BigDecimal.valueOf(100), null);

        // 断言 1：生成成功且字段正确
        assertNotNull(iqcId);
        MesQcIqcDO iqc = iqcMapper.selectById(iqcId);
        assertEquals(MesQcStatusEnum.DRAFT.getStatus(), iqc.getStatus());
        assertEquals(templateId, iqc.getTemplateId());
        assertEquals("钢管-01 来料检验", iqc.getName());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(iqc.getReceivedQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(iqc.getUnqualifiedQuantity()));
        // 断言 2：检验行按模板自动生成
        verify(iqcLineService).createLinesFromTemplate(eq(iqcId), eq(templateId));
    }

    @Test
    public void testGenerateIqcDraft_idempotent() {
        // 准备数据：同一来源行已存在检验单
        Long sourceDocId = randomLongId();
        Long sourceLineId = randomLongId();
        MesQcIqcDO existing = randomPojo(MesQcIqcDO.class, o -> {
            o.setStatus(MesQcStatusEnum.DRAFT.getStatus());
            o.setSourceDocType(MesBizTypeConstants.WM_ARRIVAL_NOTICE);
            o.setSourceDocId(sourceDocId);
            o.setSourceLineId(sourceLineId);
        });
        iqcMapper.insert(existing);

        // 调用：再次自动生成
        Long iqcId = iqcService.generateIqcDraft(MesBizTypeConstants.WM_ARRIVAL_NOTICE, sourceDocId, sourceLineId,
                "AN-001", 10L, 20L, BigDecimal.valueOf(100), null);

        // 断言：返回已有编号，不重复创建
        assertEquals(existing.getId(), iqcId);
        verify(iqcLineService, never()).createLinesFromTemplate(anyLong(), anyLong());
    }

    @Test
    public void testGenerateIqcDraft_noTemplate() {
        // mock：物料未配置 IQC 检验模板
        when(templateItemService.getRequiredTemplateByItemIdAndType(anyLong(), anyInt()))
                .thenThrow(new ServiceException(cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.QC_NO_TEMPLATE));

        // 调用：返回 null，不抛异常
        Long iqcId = iqcService.generateIqcDraft(MesBizTypeConstants.WM_ARRIVAL_NOTICE,
                randomLongId(), randomLongId(), "AN-001", 10L, 20L, BigDecimal.valueOf(100), null);

        assertNull(iqcId);
        verify(iqcLineService, never()).createLinesFromTemplate(anyLong(), anyLong());
    }

    @Test
    public void testFinishIqc_writeBack_arrivalNotice() {
        // 准备数据：插入一条草稿状态的 IQC 单，来源为到货通知单
        Long sourceDocId = randomLongId();
        Long sourceLineId = randomLongId();
        MesQcIqcDO iqc = randomPojo(MesQcIqcDO.class, o -> {
            o.setStatus(MesQcStatusEnum.DRAFT.getStatus());
            o.setCheckResult(1); // 非空，满足 finishIqc 的校验
            o.setSourceDocType(MesBizTypeConstants.WM_ARRIVAL_NOTICE);
            o.setSourceDocId(sourceDocId);
            o.setSourceLineId(sourceLineId);
            o.setQualifiedQuantity(BigDecimal.valueOf(100));
            o.setUnqualifiedQuantity(BigDecimal.ZERO);
        });
        iqcMapper.insert(iqc);

        // 调用
        iqcService.finishIqc(iqc.getId());

        // 断言：验证状态更新为已完成
        MesQcIqcDO updatedIqc = iqcMapper.selectById(iqc.getId());
        assertEquals(MesQcStatusEnum.FINISHED.getStatus(), updatedIqc.getStatus());
        // 断言：验证回写到货通知单（参数使用 any 避免 BigDecimal 精度问题）
        verify(arrivalNoticeService).updateArrivalNoticeWhenIqcFinish(
                eq(sourceDocId), eq(sourceLineId), eq(iqc.getId()), any(BigDecimal.class));
        // 断言：不应该调用外协入库单的回写
        verify(outsourceReceiptService, never()).updateOutsourceReceiptWhenIqcFinish(
                anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    public void testFinishIqc_writeBack_outsourceReceipt() {
        // 准备数据：插入一条草稿状态的 IQC 单，来源为外协入库单
        Long sourceDocId = randomLongId();
        Long sourceLineId = randomLongId();
        MesQcIqcDO iqc = randomPojo(MesQcIqcDO.class, o -> {
            o.setStatus(MesQcStatusEnum.DRAFT.getStatus());
            o.setCheckResult(1);
            o.setSourceDocType(MesBizTypeConstants.WM_OUTSOURCE_RECPT);
            o.setSourceDocId(sourceDocId);
            o.setSourceLineId(sourceLineId);
            o.setQualifiedQuantity(BigDecimal.valueOf(80));
            o.setUnqualifiedQuantity(BigDecimal.valueOf(20));
        });
        iqcMapper.insert(iqc);

        // 调用
        iqcService.finishIqc(iqc.getId());

        // 断言：验证状态更新为已完成
        MesQcIqcDO updatedIqc = iqcMapper.selectById(iqc.getId());
        assertEquals(MesQcStatusEnum.FINISHED.getStatus(), updatedIqc.getStatus());
        // 断言：验证回写外协入库单（参数使用 any 避免 BigDecimal 精度问题）
        verify(outsourceReceiptService).updateOutsourceReceiptWhenIqcFinish(
                eq(sourceDocId), eq(sourceLineId), eq(iqc.getId()), any(BigDecimal.class), any(BigDecimal.class));
        // 断言：不应该调用到货通知单的回写
        verify(arrivalNoticeService, never()).updateArrivalNoticeWhenIqcFinish(
                anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    public void testFinishIqc_writeBack_noSourceDoc() {
        // 准备数据：sourceDocType 为空，不需要回写
        MesQcIqcDO iqc = randomPojo(MesQcIqcDO.class, o -> {
            o.setStatus(MesQcStatusEnum.DRAFT.getStatus());
            o.setCheckResult(1);
            o.setSourceDocType(null);
            o.setSourceDocId(null);
            o.setSourceLineId(null);
        });
        iqcMapper.insert(iqc);

        // 调用
        iqcService.finishIqc(iqc.getId());

        // 断言：验证状态更新为已完成
        MesQcIqcDO updatedIqc = iqcMapper.selectById(iqc.getId());
        assertEquals(MesQcStatusEnum.FINISHED.getStatus(), updatedIqc.getStatus());
        // 断言：不应该调用任何回写
        verify(arrivalNoticeService, never()).updateArrivalNoticeWhenIqcFinish(
                anyLong(), anyLong(), anyLong(), any());
        verify(outsourceReceiptService, never()).updateOutsourceReceiptWhenIqcFinish(
                anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    public void testFinishIqc_writeBack_sourceDocTypeDirtyData() {
        // 准备数据：sourceDocType 非空但 sourceLineId 为空，应该抛异常
        MesQcIqcDO iqc = randomPojo(MesQcIqcDO.class, o -> {
            o.setStatus(MesQcStatusEnum.DRAFT.getStatus());
            o.setCheckResult(1);
            o.setSourceDocType(MesBizTypeConstants.WM_ARRIVAL_NOTICE);
            o.setSourceDocId(randomLongId());
            o.setSourceLineId(null); // 脏数据
        });
        iqcMapper.insert(iqc);

        // 调用，并断言异常
        assertThrows(IllegalArgumentException.class, () -> iqcService.finishIqc(iqc.getId()));
    }

    @Test
    public void testFinishIqc_writeBack_unknownSourceDocType() {
        // 准备数据：sourceDocType 无法识别
        MesQcIqcDO iqc = randomPojo(MesQcIqcDO.class, o -> {
            o.setStatus(MesQcStatusEnum.DRAFT.getStatus());
            o.setCheckResult(1);
            o.setSourceDocType(99999); // 无法识别的类型
            o.setSourceDocId(randomLongId());
            o.setSourceLineId(randomLongId());
        });
        iqcMapper.insert(iqc);

        // 调用，并断言异常
        assertThrows(IllegalArgumentException.class, () -> iqcService.finishIqc(iqc.getId()));
    }

    @Test
    public void testFinishIqc_checkResultEmpty() {
        // 准备数据：checkResult 为空，应该报错
        MesQcIqcDO iqc = randomPojo(MesQcIqcDO.class, o -> {
            o.setStatus(MesQcStatusEnum.DRAFT.getStatus());
            o.setCheckResult(null); // 检测结论为空
            o.setSourceDocType(null);
        });
        iqcMapper.insert(iqc);

        // 调用，应该抛异常（检测结论必填）
        assertThrows(Exception.class, () -> iqcService.finishIqc(iqc.getId()));
    }

    @Test
    public void testFinishIqc_notDraftStatus() {
        // 准备数据：已完成状态，不能再次完成
        MesQcIqcDO iqc = randomPojo(MesQcIqcDO.class, o -> {
            o.setStatus(MesQcStatusEnum.FINISHED.getStatus());
            o.setCheckResult(1);
        });
        iqcMapper.insert(iqc);

        // 调用，应该抛异常（不是草稿状态）
        assertThrows(Exception.class, () -> iqcService.finishIqc(iqc.getId()));
    }

}
