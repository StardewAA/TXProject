#!/usr/bin/env python3
"""Deprecated safety stub.

This legacy generator created member-side supplier operators, disabled backend
system users and embedded a shared initial password. Section 21 moved supplier
operations to explicitly authorized ``system_user`` accounts, so regenerating
that catalog would silently undo the current security model.

Keep the file name only to make accidental calls fail with an actionable
message. Product catalog changes must use a new data-only migration; account
and role migration must use ``2026-09-21-mall-section21-admin-business.sql``.
"""

raise SystemExit(
    "已废止：该生成器会破坏第21节后台经营账号模型；"
    "请使用数据专用脚本和 2026-09-21-mall-section21-admin-business.sql"
)
