# txgy-module-bidding 独立仓库说明

## 1. 为什么要拆

多个 AI 并行在父仓库 `D:/Code/IDEA/txgy/txgycloud` 里改动，共用同一个暂存区与工作树：
一个 AI 的 `git add -A` / `git commit` 会把另一个 AI 未完成的改动一起提交，回滚与排查互相干扰。
本模块（bidding，含电子资质域）改动最密集，故单独建仓隔离。

- 本目录：独立仓库，分支 `main`，首个提交 `72dc7e6`（快照，5493 个文件，`target/` 等构建产物已忽略）；
- 父仓库：`txgycloud`，分支 `feat/bidding-notice-integration`，目前**仍然跟踪**本目录的 1158 个文件 → 需要执行下面第 2 节把它移出。

## 2. 父仓库侧要执行的命令（必须在父目录执行，本仓库无权改动父目录）

```bash
cd D:/Code/IDEA/txgy/txgycloud

# ① 把本模块移出父仓库索引（--cached：只改索引，磁盘文件原样保留，不要漏掉 --cached）
git rm -r --cached txgy-module-bidding

# ② 让父仓库忽略本模块
printf 'txgy-module-bidding/\n' >> .gitignore

# ③ 提交这次拆分
git add .gitignore
git commit -m "chore: txgy-module-bidding 拆为独立仓库（父仓库不再跟踪其文件）"
```

执行后：父仓库 `git status` 不再显示本模块的任何改动；本模块的改动只进独立仓库。
**父仓库的历史不会丢**：拆分前的提交记录仍在父仓库日志里，只是此后不再跟进。

## 3. 三个 AI 的协作建议（重要）

独立仓库只解决"跨模块互相暂存/提交"的问题；**如果另外两个 AI 也在改本模块的文件，文件级冲突依旧存在**。建议再叠加一层隔离：

| 方式 | 命令 | 隔离度 | 说明 |
| --- | --- | --- | --- |
| 每人一个分支（推荐起步） | `git switch -c ai/<名字>` | 提交历史隔离，工作树共享 | 各提各的分支，合并时再 review |
| 每人一个 worktree（推荐） | `git worktree add ../wt-<名字> -b ai/<名字>` | **工作树 + 索引都隔离** | 各自在 `../wt-xxx` 目录里干活，互不覆盖文件 |
| 各自 clone | `git clone . ../clone-<名字>` | 完全隔离 | 需要手动同步，适合长周期并行 |

## 4. 回滚（如果不想拆了）

```bash
# 在父目录
git reset -- .gitignore          # 撤销 .gitignore 改动
git add txgy-module-bidding      # 把文件重新纳入父仓库索引
# 在本模块
rm -rf .git                      # 删掉嵌套仓库（不影响工作区文件）
```

## 5. 注意事项

- 首个提交是**快照**，其中包含当时其他 AI 正在改动的文件内容（如 `README.md`、`sql/master-bidding-sync.sql`）——这是有意为之，用于锁定一个可回退的基线；
- 提交时大量 `LF will be replaced by CRLF` 警告是 Windows 换行策略导致，可忽略；若要统一，可加 `.gitattributes`（`* text=auto`）；
- 仓库里包含 `千问中间脚本和文件存放位置/`（备份文档与 raw HTML），父仓库原本也跟踪它们，故保持一致；若想瘦身可从本仓库移除（不影响父仓库）。
