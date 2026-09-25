# 学习总控台 Personal

一个完全个人使用的原生 Android 学习记录与分析 App。无需注册登录；成绩、学习记录、任务、薄弱点、AI 报告和设置默认只保存在手机本地。

## 当前版本

- `versionName`: `1.1.0`（构建时由 `APP_VERSION_NAME` 环境变量传入，默认 `1.0.0`）
- `versionCode`: `2`（构建时由 `APP_VERSION_CODE` 环境变量传入，默认 `1`）
- `applicationId`: `cn.study.personal`
- 最低 Android 版本：Android 8.0（API 26）
- 目标 Android 版本：Android 15（API 35）

## 主要功能

- 首页：今日任务、完成率、学习时长、连续学习天数、成绩趋势、目标差距和近期薄弱点。
- 成绩：考试类型、各科成绩与满分、班级/年级名次、备注、得分率趋势及本地统计。未填写科目按缺失处理。
- 学习：任务、学习记录、专注计时器、今日/周/月时长、投入占比和任务完成率。
- 薄弱点：知识点、具体问题、九类错因、掌握状态、搜索筛选、高频问题和掌握变化。
- AI：成绩分析、学习状态分析、今日计划、周报、隐藏问题发现、考前冲刺方案、计划复盘。
- 我的：科目与目标、深浅色、DeepSeek Key、API 测试、Token/费用统计、历史报告和完整备份。

所有 AI 功能都要经过“点击功能 → 选择数据 → 预览 → 确认发送”才会产生一次 API 请求。App 没有定时任务、后台请求或自动重试。AI 建议任务只有再次勾选确认后才写入任务表。

## DeepSeek

安装后进入 **我的 → DeepSeek 设置** 填写 Key。Key 使用 Android Keystore 加密后保存在本机，不在源码、APK 默认配置、普通备份或 GitHub 仓库中。模型名称和最大输出 Token 可以自行修改。API 测试也会产生一次请求。

## 手机通过 GitHub Actions 构建

仓库必须设置以下四个 Actions Secrets，值在单独交付的私密签名包 `setup-github-secrets.txt` 中：

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

手机操作步骤：

1. 打开仓库 **Settings → Secrets and variables → Actions**，依次建立四个 Repository secrets。
2. 打开 **Actions → Build APK → Run workflow**。
3. 首次填写 `version_name=1.0.0`、`version_code=1`；以后每次更新都增大 `version_code`。
4. 构建完成后打开该次运行，在 **Artifacts** 下载压缩包，解压并安装 APK。
5. 发布正式版本时也可创建形如 `v1.0.1` 的 tag；工作流会把 APK 附加到 GitHub Release。

## 更新不丢数据的条件

新版必须同时满足：`applicationId` 仍为 `cn.study.personal`、使用同一份 `personal-release.jks` 签名、`versionCode` 比手机已安装版本大。数据库版本变化时必须编写升级迁移，不能清空重建。安装新版前仍建议在 App 中导出 JSON 备份。

## 本地数据和备份

普通备份为可读 JSON，包含学习数据、目标、AI 报告、Token 记录和必要设置，不包含 DeepSeek Key。导入会先完整校验；覆盖导入前，App 会在内部生成一份安全快照并允许导出。卸载 App 会删除本地数据库和加密 Key，因此请定期导出备份。

## 构建环境

项目使用 Java 17、Android Gradle Plugin 8.7.3、Gradle 8.9、compileSdk 35。正式构建命令：

```bash
./gradlew assembleRelease
```

签名文件、密码、DeepSeek Key、备份 JSON 和 `local.properties` 都被 `.gitignore` 排除。

## 隐私

没有广告 SDK、行为追踪或第三方统计。联网权限仅供用户主动发起 DeepSeek 请求使用。App 不收集姓名、学校等身份信息。
