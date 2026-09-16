package cn.iocoder.txgy.module.cms.service.sync;

import cn.iocoder.txgy.module.cms.enums.CmsResourceTypeEnum;

import java.time.Instant;
import java.util.Objects;

/**
 * MySQL 同步任务模型草案，不是已映射的数据库实体。
 * UPSERT 表示按来源最新可见版本更新；REMOVE 表示下架/删除索引，不等于资料失效。
 * 租户及权限变更也须入任务。实际表、唯一约束、租约和失败日志待实现。
 */
public record CmsIndexSyncTask(String taskId, long tenantId, CmsResourceTypeEnum resourceType,
                               String sourceId, long revision, Operation operation, Status status,
                               int attempts, Instant nextAttemptAt, String lastError) {
    public CmsIndexSyncTask {
        if (taskId == null || taskId.isBlank() || tenantId <= 0 || sourceId == null
                || sourceId.isBlank() || revision < 1 || attempts < 0) {
            throw new IllegalArgumentException("同步任务身份、修订号和重试次数无效");
        }
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(status, "status");
    }

    public enum Operation { UPSERT, REMOVE }
    public enum Status { PENDING, PROCESSING, RETRY, SUCCEEDED, FAILED }
}
