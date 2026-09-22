package cn.iocoder.txgy.module.report.service.dataset;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.report.controller.admin.dataset.vo.*;
import cn.iocoder.txgy.module.report.dal.dataobject.dataset.ReportDatasetDO;
import cn.iocoder.txgy.module.report.dal.dataobject.dataset.ReportDatasetExecuteLogDO;
import cn.iocoder.txgy.module.report.dal.mysql.dataset.ReportDatasetExecuteLogMapper;
import cn.iocoder.txgy.module.report.dal.mysql.dataset.ReportDatasetMapper;
import cn.iocoder.txgy.module.report.framework.dataset.config.ReportDatasourceProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.report.enums.ErrorCodeConstants.*;

/**
 * 报表数据集 Service 实现类
 *
 * 设计要点：
 * 1. **取数入口唯一**：报表/大屏只能按数据集编码取数，SQL 由服务端登记与审计；
 * 2. **租户强制注入**：数据集声明 tenantColumn 后，服务端追加等值条件，前端无法覆盖；
 * 3. **资源保护**：行数上限 + 查询超时 + 只读数据源，三重约束；
 * 4. **全程留痕**：无论成功失败都写审计日志。
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
@Validated
public class ReportDatasetServiceImpl implements ReportDatasetService {

    /**
     * 平台级数据集归属租户
     */
    private static final Long PLATFORM_TENANT_ID = 0L;

    /**
     * 业务分组兜底值：未选择分组的数据集归入该分组
     */
    private static final String DEFAULT_BIZ_GROUP = "自定义";

    @Resource
    private ReportDatasetMapper datasetMapper;
    @Resource
    private ReportDatasetExecuteLogMapper executeLogMapper;
    @Resource
    private ReportQueryExecutor queryExecutor;
    @Resource
    private ReportDatasourceProperties datasourceProperties;

    @Override
    public Long createDataset(ReportDatasetSaveReqVO reqVO) {
        validateDatasetSave(reqVO, null);
        ReportDatasetDO dataset = BeanUtils.toBean(reqVO, ReportDatasetDO.class);
        dataset.setBizGroups(joinGroups(reqVO.getBizGroups()));
        dataset.setParamDefinitions(JsonUtils.toJsonString(
                reqVO.getParams() == null ? Collections.emptyList() : reqVO.getParams()));
        dataset.setColumnLabels(JsonUtils.toJsonString(
                reqVO.getColumnLabelList() == null ? Collections.emptyList() : reqVO.getColumnLabelList()));
        dataset.setMaxRows(effectiveMaxRows(reqVO.getMaxRows()));
        dataset.setTimeoutSeconds(effectiveTimeoutSeconds(reqVO.getTimeoutSeconds()));
        dataset.setTenantId(null); // 由租户插件自动填充为当前租户
        datasetMapper.insert(dataset);
        return dataset.getId();
    }

    @Override
    public void updateDataset(ReportDatasetSaveReqVO reqVO) {
        ReportDatasetDO exists = validateDatasetExists(reqVO.getId());
        validatePlatformReadonly(exists);
        validateDatasetSave(reqVO, reqVO.getId());
        ReportDatasetDO updateObj = BeanUtils.toBean(reqVO, ReportDatasetDO.class);
        updateObj.setBizGroups(joinGroups(reqVO.getBizGroups()));
        updateObj.setParamDefinitions(JsonUtils.toJsonString(
                reqVO.getParams() == null ? Collections.emptyList() : reqVO.getParams()));
        updateObj.setColumnLabels(JsonUtils.toJsonString(
                reqVO.getColumnLabelList() == null ? Collections.emptyList() : reqVO.getColumnLabelList()));
        updateObj.setMaxRows(effectiveMaxRows(reqVO.getMaxRows()));
        updateObj.setTimeoutSeconds(effectiveTimeoutSeconds(reqVO.getTimeoutSeconds()));
        datasetMapper.updateById(updateObj);
    }

    @Override
    public void deleteDataset(Long id) {
        ReportDatasetDO exists = validateDatasetExists(id);
        validatePlatformReadonly(exists);
        datasetMapper.deleteById(id);
    }

    @Override
    public ReportDatasetRespVO getDataset(Long id) {
        return convert(validateDatasetExists(id));
    }

    @Override
    public PageResult<ReportDatasetRespVO> getDatasetPage(ReportDatasetPageReqVO reqVO) {
        // 管理页同时展示本租户数据集与平台级数据集（平台级只读，不允许租户修改）
        Long tenantId = currentTenantId();
        PageResult<ReportDatasetDO> pageResult = TenantUtils.executeIgnore(() ->
                datasetMapper.selectPageByTenantIds(reqVO, Arrays.asList(PLATFORM_TENANT_ID, tenantId)));
        List<ReportDatasetRespVO> list = new ArrayList<>(pageResult.getList().size());
        pageResult.getList().forEach(dataset -> list.add(convert(dataset)));
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public List<ReportDatasetRespVO> getEnabledDatasetList() {
        Long tenantId = currentTenantId();
        List<ReportDatasetDO> list = TenantUtils.executeIgnore(() -> datasetMapper.selectList(
                new LambdaQueryWrapperX<ReportDatasetDO>()
                        .eq(ReportDatasetDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                        .in(ReportDatasetDO::getTenantId, Arrays.asList(PLATFORM_TENANT_ID, tenantId))
                        .orderByAsc(ReportDatasetDO::getCategory)
                        .orderByAsc(ReportDatasetDO::getCode)));
        List<ReportDatasetRespVO> result = new ArrayList<>(list.size());
        for (ReportDatasetDO dataset : list) {
            result.add(convert(dataset));
        }
        return result;
    }

    @Override
    public ReportDatasetExecuteRespVO executeDataset(ReportDatasetExecuteReqVO reqVO) {
        ReportDatasetDO dataset = getEnabledDatasetByCode(reqVO.getCode());
        List<ReportDatasetParamVO> definitions = parseParams(dataset.getParamDefinitions());
        int maxRows = effectiveMaxRows(dataset.getMaxRows());
        int timeoutSeconds = effectiveTimeoutSeconds(dataset.getTimeoutSeconds());
        ReportSqlGuard.PreparedSql preparedSql = ReportSqlGuard.prepare(
                dataset.getSqlText(), dataset.getTenantColumn(), definitions, reqVO.getParams(),
                maxRows, currentTenantId());
        return executeAndAudit(preparedSql, dataset.getId(), dataset.getCode(), "dataset",
                reqVO.getParams(), maxRows, timeoutSeconds);
    }

    @Override
    public Long createSimpleDataset(SimpleDatasetReqVO reqVO) {
        // 1. 校验白名单表与字段
        ReportTableCatalog.TableDef table = ReportTableCatalog.get(
                StrUtil.blankToDefault(reqVO.getTableName(), ""));
        if (table == null) {
            throw exception(DATASET_SIMPLE_TABLE_INVALID, StrUtil.blankToDefault(reqVO.getTableName(), ""));
        }
        List<String> fields = reqVO.getFields() == null
                ? Collections.emptyList()
                : reqVO.getFields().stream().distinct().collect(Collectors.toList());
        if (fields.isEmpty()) {
            throw exception(DATASET_SIMPLE_FIELD_INVALID);
        }
        java.util.Map<String, ReportTableCatalog.ColumnDef> columnMap = new java.util.LinkedHashMap<>();
        table.columns().forEach(column -> columnMap.put(column.field(), column));
        for (String field : fields) {
            if (!columnMap.containsKey(field)) {
                throw exception(DATASET_SIMPLE_FIELD_INVALID);
            }
        }
        String dateField = StrUtil.trimToNull(reqVO.getDateField());
        if (dateField != null) {
            ReportTableCatalog.ColumnDef dateColumn = columnMap.get(dateField);
            if (dateColumn == null || !dateColumn.isDate()) {
                throw exception(DATASET_SIMPLE_FIELD_INVALID);
            }
        }
        // 2. 生成 SQL：自动带租户列（供服务端强制过滤）与删除标记；时间列格式化为可读文本
        String select = fields.stream().map(field -> columnMap.get(field).isDate()
                        ? "DATE_FORMAT(" + field + ", '%Y-%m-%d %H:%i:%s') AS " + field
                        : field + " AS " + field)
                .collect(Collectors.joining(", "));
        StringBuilder sql = new StringBuilder("SELECT tenant_id AS tenant_id, ").append(select)
                .append(" FROM ").append(table.table()).append(" WHERE deleted = 0");
        List<ReportDatasetParamVO> params = new ArrayList<>();
        if (dateField != null) {
            sql.append(" AND ").append(dateField).append(" >= ${startDate}")
                    .append(" AND ").append(dateField).append(" < DATE_ADD(${endDate}, INTERVAL 1 DAY)");
            params.add(newParam("startDate", "开始日期", "2000-01-01"));
            params.add(newParam("endDate", "结束日期", "2099-12-31"));
        }
        sql.append(" ORDER BY ").append(dateField != null ? dateField : "id").append(" DESC");
        // 3. 组装并保存（复用全部治理：SQL 只读校验 / 租户强制列 / 行数上限 / 审计）
        ReportDatasetSaveReqVO saveReqVO = new ReportDatasetSaveReqVO();
        saveReqVO.setCode(StrUtil.blankToDefault(reqVO.getCode(), "r" + System.currentTimeMillis()));
        saveReqVO.setName(StrUtil.blankToDefault(reqVO.getName(), table.label() + "报表"));
        saveReqVO.setCategory(StrUtil.blankToDefault(reqVO.getCategory(), "MES"));
        // 业务分组由前端传入，留空时由 createDataset 兜底为「自定义」
        saveReqVO.setBizGroups(reqVO.getBizGroups());
        saveReqVO.setSqlText(sql.toString());
        saveReqVO.setParams(params);
        saveReqVO.setColumnLabelList(fields.stream()
                .map(field -> new ReportDatasetColumnLabelVO(field, columnMap.get(field).label()))
                .collect(Collectors.toList()));
        saveReqVO.setTenantColumn("tenant_id");
        saveReqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        saveReqVO.setOwner(StrUtil.blankToDefault(reqVO.getOwner(), ""));
        saveReqVO.setRemark(StrUtil.blankToDefault(reqVO.getRemark(), "简单模式创建"));
        return createDataset(saveReqVO);
    }

    private ReportDatasetParamVO newParam(String name, String label, String defaultValue) {
        ReportDatasetParamVO param = new ReportDatasetParamVO();
        param.setName(name);
        param.setLabel(label);
        param.setType("date");
        param.setRequired(false);
        param.setDefaultValue(defaultValue);
        return param;
    }

    @Override
    public ReportDatasetDO getEnabledDataset(String code) {
        return getEnabledDatasetByCode(code);
    }

    @Override
    public List<Map<String, Object>> queryRows(String code, Map<String, Object> params) {
        ReportDatasetExecuteReqVO reqVO = new ReportDatasetExecuteReqVO();
        reqVO.setCode(code);
        reqVO.setParams(params);
        return executeDataset(reqVO).getSource();
    }

    @Override
    public ReportDatasetExecuteRespVO executeRawSql(String sql) {
        int maxRows = effectiveMaxRows(null);
        ReportSqlGuard.PreparedSql preparedSql = ReportSqlGuard.prepareRaw(sql, maxRows);
        return executeAndAudit(preparedSql, 0L, "", "raw-sql", null, maxRows,
                effectiveTimeoutSeconds(null));
    }

    // ========== 查询执行与审计 ==========

    private ReportDatasetExecuteRespVO executeAndAudit(ReportSqlGuard.PreparedSql preparedSql,
                                                       Long datasetId, String datasetCode, String source,
                                                       Map<String, Object> params, int maxRows, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        try {
            ReportQueryExecutor.QueryResult result = queryExecutor.query(preparedSql, maxRows, timeoutSeconds, source);
            long duration = System.currentTimeMillis() - start;
            insertExecuteLog(datasetId, datasetCode, source, params, result.getRows().size(), duration, true, "");
            ReportDatasetExecuteRespVO respVO = new ReportDatasetExecuteRespVO();
            respVO.setDimensions(result.getDimensions());
            respVO.setSource(result.getRows());
            respVO.setRowCount(result.getRows().size());
            respVO.setDurationMs(duration);
            return respVO;
        } catch (RuntimeException ex) {
            long duration = System.currentTimeMillis() - start;
            insertExecuteLog(datasetId, datasetCode, source, params, 0, duration, false, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 写审计日志。审计失败不能影响业务结果，只记错误日志。
     */
    private void insertExecuteLog(Long datasetId, String datasetCode, String source, Map<String, Object> params,
                                 int rowCount, long durationMs, boolean success, String errorMessage) {
        try {
            ReportDatasetExecuteLogDO logDO = ReportDatasetExecuteLogDO.builder()
                    .datasetId(datasetId).datasetCode(datasetCode).source(source)
                    .params(params == null ? "" : StrUtil.maxLength(JsonUtils.toJsonString(params), 2000))
                    .rowCount(rowCount).durationMs(durationMs).success(success)
                    .errorMessage(StrUtil.maxLength(StrUtil.blankToDefault(errorMessage, ""), 500))
                    .userId(SecurityFrameworkUtils.getLoginUserId())
                    .build();
            executeLogMapper.insert(logDO);
        } catch (Exception ex) {
            log.error("[insertExecuteLog][数据集({}) 审计日志写入失败]", datasetCode, ex);
        }
    }

    // ========== 校验与转换 ==========

    private void validateDatasetSave(ReportDatasetSaveReqVO reqVO, Long selfId) {
        // 1. SQL 只读校验 + 租户过滤列强制声明
        ReportSqlGuard.validateSql(reqVO.getSqlText());
        if (StrUtil.isBlank(reqVO.getTenantColumn())) {
            throw exception(DATASET_TENANT_COLUMN_REQUIRED);
        }
        ReportSqlGuard.validateTenantColumn(reqVO.getTenantColumn());
        // 2. 参数名合法性
        if (reqVO.getParams() != null) {
            for (ReportDatasetParamVO param : reqVO.getParams()) {
                ReportSqlGuard.validateParamName(param.getName());
            }
        }
        // 3. 编码在租户内唯一
        ReportDatasetDO sameCode = datasetMapper.selectByCode(reqVO.getCode());
        if (sameCode != null && !sameCode.getId().equals(selfId)) {
            throw exception(DATASET_CODE_DUPLICATE);
        }
    }

    private ReportDatasetDO validateDatasetExists(Long id) {
        ReportDatasetDO dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw exception(DATASET_NOT_EXISTS);
        }
        return dataset;
    }

    /**
     * 平台级数据集（tenant_id = 0）不允许租户修改/删除
     */
    private void validatePlatformReadonly(ReportDatasetDO dataset) {
        if (PLATFORM_TENANT_ID.equals(dataset.getTenantId())) {
            throw exception(DATASET_PLATFORM_READONLY);
        }
    }

    private ReportDatasetDO getEnabledDatasetByCode(String code) {
        ReportDatasetDO dataset = datasetMapper.selectByCode(code);
        if (dataset == null) {
            // 当前租户没有，则回退平台级数据集
            dataset = TenantUtils.executeIgnore(() -> datasetMapper.selectOne(
                    new LambdaQueryWrapperX<ReportDatasetDO>()
                            .eq(ReportDatasetDO::getCode, code)
                            .eq(ReportDatasetDO::getTenantId, PLATFORM_TENANT_ID)));
        }
        if (dataset == null) {
            throw exception(DATASET_NOT_EXISTS);
        }
        if (!CommonStatusEnum.ENABLE.getStatus().equals(dataset.getStatus())) {
            throw exception(DATASET_DISABLED);
        }
        return dataset;
    }

    private ReportDatasetRespVO convert(ReportDatasetDO dataset) {
        ReportDatasetRespVO respVO = BeanUtils.toBean(dataset, ReportDatasetRespVO.class);
        respVO.setParams(parseParams(dataset.getParamDefinitions()));
        respVO.setColumnLabelList(parseColumnLabels(dataset.getColumnLabels()));
        respVO.setBizGroups(splitGroups(dataset.getBizGroups()));
        return respVO;
    }

    /**
     * 业务分组：列表 -> 逗号串（为空时兜底「自定义」）
     */
    private String joinGroups(List<String> groups) {
        if (CollUtil.isEmpty(groups)) {
            return DEFAULT_BIZ_GROUP;
        }
        String joined = groups.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
        return StrUtil.isBlank(joined) ? DEFAULT_BIZ_GROUP : joined;
    }

    /**
     * 业务分组：逗号串 -> 列表
     */
    private List<String> splitGroups(String groups) {
        if (StrUtil.isBlank(groups)) {
            return Collections.emptyList();
        }
        return Arrays.stream(groups.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

        return respVO;
    }

    private List<ReportDatasetParamVO> parseParams(String paramDefinitions) {
        if (StrUtil.isBlank(paramDefinitions)) {
            return Collections.emptyList();
        }
        try {
            List<ReportDatasetParamVO> params = JsonUtils.parseArray(paramDefinitions, ReportDatasetParamVO.class);
            return params == null ? Collections.emptyList() : params;
        } catch (Exception ex) {
            log.warn("[parseParams][参数定义解析失败：{}]", paramDefinitions, ex);
            return Collections.emptyList();
        }
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

    private Integer effectiveMaxRows(Integer datasetMaxRows) {
        int globalMax = datasourceProperties.getMaxRows() == null || datasourceProperties.getMaxRows() <= 0
                ? 5000 : datasourceProperties.getMaxRows();
        if (datasetMaxRows == null || datasetMaxRows <= 0) {
            return globalMax;
        }
        if (datasetMaxRows > globalMax) {
            log.warn("[effectiveMaxRows][数据集行数上限 {} 超过全局上限 {}，已按全局上限执行]", datasetMaxRows, globalMax);
            return globalMax;
        }
        return datasetMaxRows;
    }

    private Integer effectiveTimeoutSeconds(Integer datasetTimeoutSeconds) {
        int globalTimeout = datasourceProperties.getTimeoutSeconds() == null || datasourceProperties.getTimeoutSeconds() <= 0
                ? 30 : datasourceProperties.getTimeoutSeconds();
        if (datasetTimeoutSeconds == null || datasetTimeoutSeconds <= 0) {
            return globalTimeout;
        }
        return Math.min(datasetTimeoutSeconds, globalTimeout);
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? PLATFORM_TENANT_ID : tenantId;
    }

}
