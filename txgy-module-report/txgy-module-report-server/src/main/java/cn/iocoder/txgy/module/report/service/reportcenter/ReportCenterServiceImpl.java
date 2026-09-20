package cn.iocoder.txgy.module.report.service.reportcenter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterDataRespVO;
import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterQueryReqVO;
import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterReportRespVO;
import cn.iocoder.txgy.module.report.controller.admin.dataset.vo.ReportDatasetColumnLabelVO;
import cn.iocoder.txgy.module.report.dal.dataobject.dataset.ReportDatasetDO;
import cn.iocoder.txgy.module.report.dal.dataobject.dataset.ReportDatasetExecuteLogDO;
import cn.iocoder.txgy.module.report.dal.mysql.dataset.ReportDatasetExecuteLogMapper;
import cn.iocoder.txgy.module.report.service.dataset.ReportDatasetService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.report.enums.ErrorCodeConstants.REPORT_CENTER_DATASET_MISSING;
import static cn.iocoder.txgy.module.report.enums.ErrorCodeConstants.REPORT_CENTER_EXPORT_FAILED;

/**
 * 报表中心 Service 实现类
 *
 * 取数统一走受控数据集（{@link ReportDatasetService}）：
 * 租户隔离、行数上限、超时、审计全部沿用，报表中心只做「业务化包装 + 导出」。
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
@Validated
public class ReportCenterServiceImpl implements ReportCenterService {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    /**
     * 「常用」统计窗口（天）
     */
    private static final int FREQUENT_DAYS = 30;

    /**
     * 自助创建的数据集所属分组
     */
    private static final String GROUP_CUSTOM = "自定义";

    @Resource
    private ReportDatasetService datasetService;
    @Resource
    private ReportDatasetExecuteLogMapper executeLogMapper;

    @Override
    public List<ReportCenterReportRespVO> getReportList() {
        // 只返回数据源已登记且启用的报表，避免业务人员点到空报表
        List<cn.iocoder.txgy.module.report.controller.admin.dataset.vo.ReportDatasetRespVO> enabledDatasets =
                datasetService.getEnabledDatasetList();
        Set<String> curatedCodes = new HashSet<>();
        List<ReportCenterReportRespVO> list = new ArrayList<>();
        // 近 30 天查询次数：供前端「常用」分组排序
        Map<String, Integer> queryCounts = loadQueryCounts();
        // 1. 精选定义（带状态中文映射等增强）
        for (MesReportDefinition definition : MesReportDefinition.values()) {
            curatedCodes.add(definition.getCode());
            cn.iocoder.txgy.module.report.controller.admin.dataset.vo.ReportDatasetRespVO dataset =
                    enabledDatasets.stream()
                            .filter(vo -> definition.getCode().equals(vo.getCode()))
                            .findFirst().orElse(null);
            if (dataset == null) {
                log.warn("[getReportList][报表({}/{}) 数据源未登记或已停用，暂不展示]",
                        definition.getCode(), definition.getName());
                continue;
            }
            ReportCenterReportRespVO respVO = new ReportCenterReportRespVO();
            respVO.setCode(definition.getCode());
            respVO.setName(definition.getName());
            respVO.setDescription(definition.getDescription());
            respVO.setParams(definition.getParams().stream()
                    .map(param -> new ReportCenterReportRespVO.Param(param.name(), param.label()))
                    .collect(Collectors.toList()));
            // 分组以数据集登记的业务分组为准（页面可维护）；未迁移时回退枚举默认值
            respVO.setGroups(CollUtil.isEmpty(dataset.getBizGroups())
                    ? definition.getGroups() : dataset.getBizGroups());
            respVO.setColumnLabels(definition.getColumns().stream()
                    .map(MesReportDefinition.ColumnDef::label)
                    .collect(Collectors.toList()));
            respVO.setQueryCount(queryCounts.getOrDefault(definition.getCode(), 0));
            list.add(respVO);
        }
        // 2. 简单模式自助创建的数据集（带中文列标签，且未与精选定义重复）也进入报表中心
        for (cn.iocoder.txgy.module.report.controller.admin.dataset.vo.ReportDatasetRespVO vo : enabledDatasets) {
            if (curatedCodes.contains(vo.getCode()) || StrUtil.isBlank(joinLabels(vo.getColumnLabelList()))) {
                continue;
            }
            ReportCenterReportRespVO generic = new ReportCenterReportRespVO();
            generic.setCode(vo.getCode());
            generic.setName(vo.getName());
            generic.setDescription(StrUtil.blankToDefault(vo.getRemark(), "自定义报表"));
            generic.setParams(vo.getParams().stream()
                    .map(param -> new ReportCenterReportRespVO.Param(param.getName(), param.getLabel()))
                    .collect(Collectors.toList()));
            generic.setGroups(CollUtil.isEmpty(vo.getBizGroups())
                    ? List.of(GROUP_CUSTOM) : vo.getBizGroups());
            generic.setColumnLabels(vo.getColumnLabelList().stream()
                    .map(ReportDatasetColumnLabelVO::getLabel)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList()));
            generic.setQueryCount(queryCounts.getOrDefault(vo.getCode(), 0));
            list.add(generic);
        }
        return list;
    }

    /**
     * 统计近 {@link #FREQUENT_DAYS} 天各数据集的成功查询次数
     *
     * 供前端「常用」分组排序。统计失败不影响报表清单返回，按 0 处理。
     */
    private Map<String, Integer> loadQueryCounts() {
        try {
            QueryWrapper<ReportDatasetExecuteLogDO> wrapper = new QueryWrapper<>();
            wrapper.select("dataset_code", "COUNT(*) AS query_count")
                    .eq("source", "dataset")
                    .eq("success", true)
                    .ge("create_time", LocalDateTime.now().minusDays(FREQUENT_DAYS))
                    .groupBy("dataset_code");
            List<Map<String, Object>> rows = executeLogMapper.selectMaps(wrapper);
            Map<String, Integer> counts = new HashMap<>(rows.size());
            for (Map<String, Object> row : rows) {
                String code = asString(row, "dataset_code");
                Integer count = asInteger(row, "query_count");
                if (StrUtil.isNotBlank(code) && count != null) {
                    counts.put(code, count);
                }
            }
            return counts;
        } catch (Exception ex) {
            log.warn("[loadQueryCounts][常用报表统计失败，按 0 处理]", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * 从 selectMaps 结果中取字符串（兼容不同驱动的列名大小写）
     */
    private String asString(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null) {
                return String.valueOf(entry.getValue());
            }
        }
        return null;
    }

    /**
     * 从 selectMaps 结果中取整数（兼容不同驱动的列名大小写）
     */
    private Integer asInteger(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() instanceof Number) {
                return ((Number) entry.getValue()).intValue();
            }
        }
        return null;
    }

    @Override
    public ReportCenterDataRespVO query(ReportCenterQueryReqVO reqVO) {
        ResolvedReport resolved = resolve(reqVO.getCode());
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = datasetService.queryRows(resolved.code(), reqVO.getParams());
        long duration = System.currentTimeMillis() - start;
        return buildData(resolved, rows, duration);
    }

    @Override
    public void exportExcel(ReportCenterQueryReqVO reqVO, HttpServletResponse response) {
        ResolvedReport resolved = resolve(reqVO.getCode());
        List<Map<String, Object>> rows = datasetService.queryRows(resolved.code(), reqVO.getParams());
        List<List<String>> head = resolved.columns().stream()
                .map(column -> Collections.singletonList(column.label()))
                .collect(Collectors.toList());
        List<List<Object>> data = buildRows(resolved.columns(), rows);
        String filename = resolved.name() + "_" + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".xlsx";
        try {
            ExcelUtils.write(response, filename, resolved.name(), head, data);
        } catch (IOException ex) {
            log.error("[exportExcel][报表({}) 导出失败]", resolved.code(), ex);
            throw exception(REPORT_CENTER_EXPORT_FAILED, ex.getMessage());
        }
    }

    /**
     * 解析结果
     */
    private record ResolvedReport(String code, String name,
                                  List<MesReportDefinition.ColumnDef> columns) {
    }

    /**
     * 优先走精选定义（带状态中文映射等增强）；否则回退到简单模式数据集（按列标签输出中文表头）
     */
    private ResolvedReport resolve(String code) {
        for (MesReportDefinition definition : MesReportDefinition.values()) {
            if (definition.getCode().equals(code)) {
                checkDatasetAvailable(code);
                return new ResolvedReport(code, definition.getName(), definition.getColumns());
            }
        }
        ReportDatasetDO dataset = datasetService.getEnabledDataset(code);
        List<MesReportDefinition.ColumnDef> columns =
                parseColumnLabels(dataset.getColumnLabels()).stream()
                        .map(label -> MesReportDefinition.ColumnDef.of(label.getField(), label.getLabel()))
                        .collect(Collectors.toList());
        if (columns.isEmpty()) {
            // 没有中文列标签的数据集不在报表中心展示（避免英文字段直出给业务人员）
            throw exception(cn.iocoder.txgy.module.report.enums.ErrorCodeConstants.REPORT_CENTER_NOT_OPEN);
        }
        return new ResolvedReport(code, dataset.getName(), columns);
    }

    /**
     * 校验报表数据源可用（对业务人员给出可读提示）
     */
    private void checkDatasetAvailable(String code) {
        boolean available = datasetService.getEnabledDatasetList().stream()
                .anyMatch(vo -> vo.getCode().equals(code));
        if (!available) {
            throw exception(REPORT_CENTER_DATASET_MISSING);
        }
    }

    private String joinLabels(List<ReportDatasetColumnLabelVO> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        return labels.stream().map(ReportDatasetColumnLabelVO::getField).collect(Collectors.joining(","));
    }

    private List<ReportDatasetColumnLabelVO> parseColumnLabels(String columnLabels) {
        if (StrUtil.isBlank(columnLabels)) {
            return Collections.emptyList();
        }
        try {
            List<ReportDatasetColumnLabelVO> labels =
                    JsonUtils.parseArray(columnLabels, ReportDatasetColumnLabelVO.class);
            return labels == null ? Collections.emptyList() : labels;
        } catch (Exception ex) {
            log.warn("[parseColumnLabels][列标签解析失败：{}]", columnLabels, ex);
            return Collections.emptyList();
        }
    }

    /**
     * 按列定义顺序整理行数据：状态等取值转中文，空值转空字符串（Excel 更干净）
     */
    private List<List<Object>> buildRows(List<MesReportDefinition.ColumnDef> columns,
                                         List<Map<String, Object>> rows) {
        List<List<Object>> data = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            List<Object> line = new ArrayList<>(columns.size());
            for (MesReportDefinition.ColumnDef column : columns) {
                Object value = row.get(column.field());
                line.add(formatValue(value, column.valueLabels()));
            }
            data.add(line);
        }
        return data;
    }

    private Object formatValue(Object value, Map<String, String> valueLabels) {
        if (value == null) {
            return "";
        }
        if (!valueLabels.isEmpty()) {
            String label = valueLabels.get(String.valueOf(value));
            if (label != null) {
                return label;
            }
        }
        return value;
    }

    private ReportCenterDataRespVO buildData(ResolvedReport resolved, List<Map<String, Object>> rows, long duration) {
        ReportCenterDataRespVO respVO = new ReportCenterDataRespVO();
        respVO.setHeaders(resolved.columns().stream().map(MesReportDefinition.ColumnDef::label)
                .collect(Collectors.toList()));
        respVO.setRows(buildRows(resolved.columns(), rows));
        respVO.setRowCount(rows.size());
        respVO.setDurationMs(duration);
        return respVO;
    }

}
