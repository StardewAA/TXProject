package cn.iocoder.txgy.module.mes.service.qc.sample;

import cn.iocoder.txgy.module.mes.dal.dataobject.qc.template.MesQcTemplateItemDO;
import cn.iocoder.txgy.module.mes.enums.qc.MesQcSampleTypeEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MesQcSamplePlanServiceImpl} 的单元测试
 *
 * 纯计算逻辑测试，不依赖数据库。
 *
 * @author OPENLAB BS
 */
public class MesQcSamplePlanServiceImplTest {

    private final MesQcSamplePlanServiceImpl samplePlanService = new MesQcSamplePlanServiceImpl();

    @Test
    public void testCalculate_full() {
        // 全检：抽样数 = 基数
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(1L).sampleType(MesQcSampleTypeEnum.FULL.getType()).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, BigDecimal.valueOf(100));

        assertEquals(MesQcSampleTypeEnum.FULL.getType(), snapshot.getSampleType());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(snapshot.getCheckQuantity()));
        assertEquals(0, BigDecimal.valueOf(100).compareTo(snapshot.getBaseQuantity()));
        assertEquals(1L, snapshot.getTemplateItemId());
        assertNotNull(snapshot.getSnapshotTime());
    }

    @Test
    public void testCalculate_fixedQuantity_lessThanBase() {
        // 固定数量 5，基数 100 => 5
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(2L).sampleType(MesQcSampleTypeEnum.FIXED_QUANTITY.getType())
                .sampleFixedQuantity(BigDecimal.valueOf(5)).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, BigDecimal.valueOf(100));

        assertEquals(0, BigDecimal.valueOf(5).compareTo(snapshot.getCheckQuantity()));
    }

    @Test
    public void testCalculate_fixedQuantity_greaterThanBase() {
        // 固定数量 50，基数 20 => min(50,20) = 20
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(3L).sampleType(MesQcSampleTypeEnum.FIXED_QUANTITY.getType())
                .sampleFixedQuantity(BigDecimal.valueOf(50)).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, BigDecimal.valueOf(20));

        assertEquals(0, BigDecimal.valueOf(20).compareTo(snapshot.getCheckQuantity()));
    }

    @Test
    public void testCalculate_ratio_roundUp() {
        // 比例 10%，基数 105 => 10.5 向上取整 = 11
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(4L).sampleType(MesQcSampleTypeEnum.RATIO.getType())
                .sampleRatio(BigDecimal.valueOf(10)).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, BigDecimal.valueOf(105));

        assertEquals(0, BigDecimal.valueOf(11).compareTo(snapshot.getCheckQuantity()));
        assertEquals(0, BigDecimal.valueOf(10).compareTo(snapshot.getRatio()));
    }

    @Test
    public void testCalculate_ratio_exactDivision() {
        // 比例 10%，基数 100 => 10
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(5L).sampleType(MesQcSampleTypeEnum.RATIO.getType())
                .sampleRatio(BigDecimal.valueOf(10)).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, BigDecimal.valueOf(100));

        assertEquals(0, BigDecimal.valueOf(10).compareTo(snapshot.getCheckQuantity()));
    }

    @Test
    public void testCalculate_ratio_notExceedBase() {
        // 比例 150%，基数 10 => 不超基数 = 10
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(6L).sampleType(MesQcSampleTypeEnum.RATIO.getType())
                .sampleRatio(BigDecimal.valueOf(150)).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, BigDecimal.valueOf(10));

        assertEquals(0, BigDecimal.valueOf(10).compareTo(snapshot.getCheckQuantity()));
    }

    @Test
    public void testCalculate_ratio_decimalBase() {
        // 比例 30%，基数 3.5 => 1.05 向上取整 = 2
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(7L).sampleType(MesQcSampleTypeEnum.RATIO.getType())
                .sampleRatio(BigDecimal.valueOf(30)).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, new BigDecimal("3.5"));

        assertEquals(0, BigDecimal.valueOf(2).compareTo(snapshot.getCheckQuantity()));
    }

    @Test
    public void testCalculate_noTemplateItem_downgradeFull() {
        // 未绑定方案 => 降级全检
        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(null, BigDecimal.valueOf(30));

        assertEquals(MesQcSampleTypeEnum.FULL.getType(), snapshot.getSampleType());
        assertEquals(0, BigDecimal.valueOf(30).compareTo(snapshot.getCheckQuantity()));
        assertNull(snapshot.getTemplateItemId());
    }

    @Test
    public void testCalculate_invalidSampleType_downgradeFull() {
        // 抽样方式非法 => 降级全检
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(8L).sampleType(99).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, BigDecimal.valueOf(40));

        assertEquals(MesQcSampleTypeEnum.FULL.getType(), snapshot.getSampleType());
        assertEquals(0, BigDecimal.valueOf(40).compareTo(snapshot.getCheckQuantity()));
    }

    @Test
    public void testCalculate_fixedQuantityMissing_downgradeFull() {
        // 固定数量方式但未配置数量 => 降级全检
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(9L).sampleType(MesQcSampleTypeEnum.FIXED_QUANTITY.getType()).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, BigDecimal.valueOf(60));

        assertEquals(MesQcSampleTypeEnum.FULL.getType(), snapshot.getSampleType());
        assertEquals(0, BigDecimal.valueOf(60).compareTo(snapshot.getCheckQuantity()));
    }

    @Test
    public void testCalculate_ratioMissing_downgradeFull() {
        // 按比例方式但未配置比例 => 降级全检
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(10L).sampleType(MesQcSampleTypeEnum.RATIO.getType()).build();

        MesQcSamplePlanSnapshot snapshot = samplePlanService.calculate(templateItem, BigDecimal.valueOf(70));

        assertEquals(MesQcSampleTypeEnum.FULL.getType(), snapshot.getSampleType());
        assertEquals(0, BigDecimal.valueOf(70).compareTo(snapshot.getCheckQuantity()));
    }

    @Test
    public void testCalculate_nullOrZeroBase() {
        // 基数为空 / 0 => 抽样数 0
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(11L).sampleType(MesQcSampleTypeEnum.RATIO.getType())
                .sampleRatio(BigDecimal.valueOf(10)).build();

        assertEquals(0, BigDecimal.ZERO.compareTo(
                samplePlanService.calculate(templateItem, null).getCheckQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                samplePlanService.calculate(templateItem, BigDecimal.ZERO).getCheckQuantity()));
    }

    @Test
    public void testToJson_containsPlanFields() {
        MesQcTemplateItemDO templateItem = MesQcTemplateItemDO.builder()
                .id(12L).sampleType(MesQcSampleTypeEnum.RATIO.getType())
                .sampleRatio(BigDecimal.valueOf(10)).build();

        String json = samplePlanService.toJson(samplePlanService.calculate(templateItem, BigDecimal.valueOf(105)));

        assertNotNull(json);
        assertTrue(json.contains("\"sampleType\":3"));
        assertTrue(json.contains("\"sampleTypeName\":\"按比例\""));
        assertTrue(json.contains("\"baseQuantity\":105"));
        assertTrue(json.contains("\"checkQuantity\":11"));
        assertTrue(json.contains("\"templateItemId\":12"));
    }

    @Test
    public void testToJson_null() {
        assertNull(samplePlanService.toJson(null));
    }

}
