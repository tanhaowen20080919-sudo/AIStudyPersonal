# 发布检查表

- [ ] 先在 App 中导出 JSON 备份。
- [ ] 修改或在工作流中填写更大的 `versionCode`。
- [ ] 保持 `applicationId=cn.study.personal`。
- [ ] 保持同一份 `personal-release.jks` 与 alias。
- [ ] 如果数据库结构升级，增加明确迁移逻辑，禁止删除旧数据。
- [ ] Actions 构建成功后下载 APK。
- [ ] 用 Android 系统安装器覆盖安装，确认旧数据仍能读取。
- [ ] 再检查新增、编辑、导出和一次主动 AI 请求。
