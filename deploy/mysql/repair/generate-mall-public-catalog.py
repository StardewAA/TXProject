#!/usr/bin/env python3
"""Deprecated safety stub for the legacy public-mall account generator.

The old generator embedded a shared password and granted supplier operations to
member accounts. Both behaviours conflict with Section 21. A replacement must
generate catalog data only and must never provision credentials.
"""

raise SystemExit(
    "已废止：禁止生成共享密码或 member 供应经营账号；"
    "请使用 2026-09-21-mall-section21-admin-business.sql 完成安全授权"
)
