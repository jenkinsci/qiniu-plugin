# 七牛云 Jenkins 插件

## 安装

从 Jenkins 插件列表中找到 Qiniu 插件，安装

![七牛插件安装](https://user-images.githubusercontent.com/321962/73899773-96ddd180-48c8-11ea-8395-4acf746e2817.png)

## 构建

克隆代码仓库

```bash
git clone git@github.com:jenkinsci/qiniu-plugin.git
```

进入插件目录并构建插件

```bash
cd qiniu-plugin
mvn -Djenkins.version=2.217 -Duse-jenkins-bom package
```

## 安装依赖

- OpenJDK 1.8
- Jenkins 2.217+

## 使用插件

### 全局配置

- 点击 `Manage Jenkins` 进入管理界面。
- 点击 `Configure System` 进入配置界面。
- 点击 `Artifact Management for Builds` 按钮，选择 `Qiniu Artifact Manager`。
- 在出现的 `Qiniu Artifact Manager` 配置栏中，依次填写 `Access Key`，`Secret Key` 和 `Bucket Name`，注意 `Bucket Name` 必须是七牛账户中已有的存储空间名称。
- 可以点击旁边的 `Advanced` 按钮，将出现更多配置项，这里的配置项都是可选的。
	- `Archive as infrequent storage object` 表示以低频存储的方式存储归档文件，推荐打开。
	- `Object Name Prefix` 表示在存储空间中的对象名称前缀。
	- `Bucket Download Domain` 表示存储空间绑定的下载域名，如果不填，则从存储空间中选择一个下载域名。但如果在存储空间中没有绑定任何下载域名，则该项必填。
	- `Use HTTPs Protocol` 表示是否使用 HTTPS 传输协议，默认使用 HTTP 传输协议。
	- `Qiniu Uc Domain`，`Qiniu Rs Domain`，`Qiniu API Domain` 都仅在使用七牛私有云时才有必要修改配置，默认使用公有云的配置。
	- `Apply for all jobs` 表示对所有 Job 全部启用七牛归档存储。
- 点击 `Save` 按钮保存。

### 配置任务

- 如果在全局配置中没有勾选 `Apply for all jobs`，则需要为每个需要的 Job 启用七牛归档存储，配置方法如下：
  - 在 Job 的配置界面，点击 `Post-build Actions` 下拉框里的 `Archive the artifacts to Qiniu` 选项，将添加一个配置框。
  - 在 `Files to archive` 里输入要归档的构建结果路径，可以使用通配符。
  - 可以点击旁边的 `Advanced` 按钮，将出现更多配置项，这里的配置项都是可选的。
    - `Excludes` 表示要从构建结果中排除一部分文件，依然可以使用通配符。
    - `Do not fail build if archiving returns nothing` 表示允许空的归档文件。
    - `Archive artifacts only if build is successful` 表示仅当构建成功才会归档。
    - `Use default excludes` 表示自动将 SCM 软件用的配置文件或数据文件排除，不予归档。
    - `Treat include and exclude patterns as case sensitive` 表示归档结果路径为大小写敏感。
  - 点击 `Save` 按钮保存。

### 流水线

- 如果在全局配置中没有勾选 `Apply for all jobs`，则在 Pipeline 配置界面中，可以使用 `archiveArtifactsToQiniu` 指令将文件归档到七牛云存储。

#### 脚本案例

```groovy
node {
   stage('Build') {
      sh '''
rm -rf **
mkdir -p {aa,bb,cc}/{dd,ee,ff}
for file in {aa,bb,cc}/{dd,ee,ff}/{gg,hh,ii}
do
  dd if=/dev/urandom of="$file" count=1024 bs=1
done
      '''
   }
   stage('Results') {
      archiveArtifactsToQiniu allowEmptyArchive: false, caseSensitive: false, excludeFilesGlob: '', includeFilesGlob: '**', onlyIfSuccessful: false, useDefaultExcludes: false
   }
}
```

#### 参数列表

| 参数名称         | 参数类型 | 描述 | 备注                                      |
| ---------------- | -------- | -------- | ----------------------------------------- |
| includeFilesGlob | 字符串   | 要归档的构建结果路径，可以使用通配符 |必填，如果希望归档所有文件，则填写 `'**`|
| excludeFilesGlob | 字符串   | 要从构建结果中排除一部分文件，依然可以使用通配符 | 必填，如果不希望排除任何文件，则填写 `''` |
| allowEmptyArchive  | 布尔值   | 允许空的归档文件 | 必填 |
| onlyIfSuccessful | 布尔值 | 仅当构建成功才会归档 | 必填 |
| useDefaultExcludes | 布尔值 | 自动将 SCM 软件用的配置文件或数据文件排除，不予归档 |                                      |
| caseSensitive | 布尔值 | 归档结果路径为大小写敏感 |                                     |


