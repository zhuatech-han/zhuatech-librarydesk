[中文](README.md) | [English](README.en.md)

# LibraryDesk 图书借阅管理系统 · Java 21 / Spring Boot / Vue 3

**知华科技（上海如静知华信息科技有限公司）** · [官网](https://www.zhuatech.cn/)

LibraryDesk 面向小型图书馆、社区阅读室、企业阅览室和学校独立书库，管理“书目—馆藏单册—读者—借阅—续借—预约候补”的完整流通流程。馆员用单册条码办理借出、正常/损坏归还和遗失结案；读者登录后查看本馆馆藏、预约图书、查看本人借阅和续借。

**公开源码学习版／非商业源码版。** 源码公开不代表免费商用。仅限个人学习交流，未经上海如静知华信息科技有限公司书面授权不得商用，详见 [LICENSE](LICENSE)。商业部署、授权、定制和集成请联系知华科技。

## 适用场景与已实现功能

| 使用者 | 实际能力 |
| --- | --- |
| 馆员 | 书目分类与 ISBN 检查、单册条码/架位、书目归档、单册损坏/退出、读者借阅证、条码查询、借出归还、续借、候补整理 |
| 读者 | 已登录本馆馆藏查询、本人借阅记录、预约和候补位置、待领截止、取消预约、符合条件的续借 |
| 管理员 | 登录账号、密码重置、启停、角色权限、数据范围、图书馆时区、分类字典、菜单、五项流通参数 |
| 查看员 | 本馆馆藏和流通统计；不能借还或修改记录 |

数据持久化到 MySQL；服务端检查 CSRF、权限、图书馆范围、本人流通范围、记录版本和业务状态。账号停用、图书馆停用或密码重置使旧会话失效；保留最后一个完整管理员。没有流通历史的书目、可借单册和读者可删除；有历史须归档、退出或停用。

支持中文/英文界面、搜索分页、状态过滤、借阅 CSV、流通事件和审计。条码框兼容键盘输入型扫码设备，Enter 只查询，办理必须确认；实体扫描器未做硬件验收。

## 流通规则

- 默认借期 14 天、同时借阅 5 册、可续借 2 次、有效预约 3 条、待领取 48 小时。管理员可调整；既有借阅保留借期和续借上限快照，既有待领记录保留截止时间。
- 书目可以有多本单册，每本全局唯一条码；不把 ISBN 当作单册条码。ISBN 可留空；填写时校验 ISBN-10/13 检查位和本馆重复。
- 借出只允许可借或为该读者保留的单册，同馆、启用借阅资格和借阅限额均需满足。归还自动释放正常单册；损坏和遗失单册不可分配。
- 预约按书目排队，以创建时间和编号确定先后；跳过目前逾期的读者，其候补仍保留。账号/资格停用则取消该有效预约。正常归还、取消和流通操作会整理对应书目的队列。
- 领取截止到时即不可凭旧预约领取；过期状态释放、空闲单册分配在成功的流通操作或馆员“整理队列”时执行。**没有后台定时任务或自动通知**，馆员应日常整理队列。
- 应还当天仍有效；各馆本地日期晚于应还日才逾期。逾期阻止新借出、预约和续借；其他读者存在有效预约时也阻止续借。续借从原应还日增加本次借期。
- 不收罚款，不执行支付或押金扣款；遗失结案只记录业务结果。

## 实际运行页面

以下截图来自本系统实际运行的 TEST 验收数据，不代表真实客户或使用案例。空库安装不会自动加载这些书目或测试账号。

### 登录

私有账号会话登录，无共享演示口令。

![登录](docs/screenshots/01-login.jpg)

### 馆藏目录

搜索书目、分类和可借单册数量。

![馆藏目录](docs/screenshots/02-catalog.jpg)

### 书目与单册

单册条码、架位、状态与真实流通历史。

![书目与单册](docs/screenshots/03-title.jpg)

### 借还柜台

输入单册条码后核对读者与借还操作。

![借还柜台](docs/screenshots/04-desk.jpg)

### 读者管理

登录账号关联借阅证和借阅资格。

![读者管理](docs/screenshots/05-patrons.jpg)

### 读者端

本人预约、领取截止、候补位置和取消。

![读者端](docs/screenshots/06-reader.jpg)

### 借阅记录

真实借阅日期、应还日期、续借次数与状态。

![借阅记录](docs/screenshots/07-loans.jpg)

### 预约队列

按书目候补，归还释放后按资格与顺序分配。

![预约队列](docs/screenshots/08-holds.jpg)

### 登录账号

管理员维护账号、角色和所属图书馆。

![登录账号](docs/screenshots/09-accounts.jpg)

### 角色与权限

权限与全部馆、本馆、本人流通范围。

![角色与权限](docs/screenshots/10-roles.jpg)

### 图书馆与参数

时区、分类、借期、续借和预约领取期限。

![图书馆与参数](docs/screenshots/11-settings.jpg)

### 流通报表

可借、借出、候补、逾期与借阅台账 CSV。

![流通报表](docs/screenshots/12-reports.jpg)

### 操作记录

查看所属范围内的真实操作记录。

![操作记录](docs/screenshots/13-audit.jpg)

### 手机读者端

响应式馆藏查询；表格横向滚动。

![手机读者端](docs/screenshots/14-mobile.jpg)

### 英文界面

业务界面中英文切换，业务数据保持原文。

![英文界面](docs/screenshots/15-english.jpg)

## 技术架构与目录

Java 21、Spring Boot 4.0.7、Spring Security、JPA/Hibernate、Flyway；Vue 3.5.43、Vite 8.1.5、Lucide；MySQL 8.4、Nginx 与 Docker Compose。浏览器通过同源 Nginx 访问 `/api/`，后端持久化到 MySQL。健康检查、登录与 CSRF 引导允许匿名访问；馆藏及其他业务需要登录。BCrypt 密码散列、HttpOnly/SameSite 会话、CSRF 和服务端数据范围独立执行。部署为单实例应用，写入通过数据库锁串行保护流通状态。

```text
backend/src/main/java/cn/zhuatech/librarydesk/  # identity, catalog, circulation
backend/src/main/resources/db/migration/     # versioned schema
backend/src/test/java/                       # HTTP + boundary tests
frontend/src/                               # Vue librarian/patron UI
frontend/public/brand/                      # logo and original contact assets
docs/screenshots/                           # actual running pages
scripts/                                   # QA, release check, backup/restore
compose.yaml                               # MySQL + backend + Nginx
.env.example                               # configuration names, no secrets
```

## 环境要求与安装启动

Docker Engine / Docker Desktop 与 Compose V2；完整镜像构建需要访问官方 Maven/npm/Docker 镜像源。建议开发机至少 4 GB 可用内存。源码调试需要 JDK 21、Maven 3.9+、Node.js 24.19.0+、npm 及 Python 3.10+。Compose 提供 MySQL；不需要手工安装本机数据库。

```sh
python3 scripts/init-env.py
docker compose -p librarydesk config --quiet
docker compose -p librarydesk up -d --build --wait --wait-timeout 240
```

访问 [http://127.0.0.1:8127/](http://127.0.0.1:8127/)，健康检查 [http://127.0.0.1:8127/actuator/health](http://127.0.0.1:8127/actuator/health)。首次账号 `admin`，密码读取本机私有 `.env` 的 `ADMIN_PASSWORD`。初始化脚本生成随机凭证并拒绝覆盖现有 `.env`；不要上传此文件。首次登录后按需修改个人密码。已有数据库不会因修改环境变量重置管理员。

## 数据库初始化与配置

`V1__identity.sql` 创建身份、角色、权限、图书馆、菜单、字典、参数和审计；`V2__circulation.sql` 创建书目、单册、读者、借阅、预约和流通事件。Flyway 按版本自动迁移，Hibernate 仅校验、不自动覆盖建表。共 15 张应用表及 Flyway 历史表。空库首次初始化一个管理员、主图书馆、4 个角色、10 项权限、11 个菜单、4 个图书分类和 5 项参数，无业务示例数据。

配置名称见 [.env.example](.env.example)。MySQL 数据保存在 Compose 命名卷。保留迁移校验和；升级前备份，新增版本迁移，禁止编辑已上线的迁移文件。

| Variable | Default / purpose |
| --- | --- |
| MYSQL_ROOT_PASSWORD | Required, unique private database root password |
| DATABASE_PASSWORD | Required, unique application database password |
| ADMIN_USERNAME | `admin`; first empty database only |
| ADMIN_PASSWORD | Required; 12–72 bytes, uppercase/lowercase/digit; first empty database only |
| WEB_PORT | `8127` |
| BIND_ADDRESS | `127.0.0.1` |
| COOKIE_SECURE | `false` for localhost HTTP; `true` behind HTTPS |
| DATABASE_URL / DATABASE_USER | Optional external MySQL; bundled default user `librarydesk` |

外部 MySQL 应使用独立最小权限账号、`sslMode=VERIFY_IDENTITY` 和受信任 CA；当前备份/恢复脚本仅支持内置 Compose 数据库。账号必须有初始化迁移所需 DDL 权限。不要把真实凭证、客户数据或备份提交到 Git。

## 首次操作与部署

1. 管理员在“图书馆与设置”维护分类、时区和借阅参数，在“登录账号”建立馆员和有读者权限的账号。
2. 馆员开通与读者账号对应的借阅证，建立书目和实际单册条码、架位。
3. 柜台输入单册条码，选择读者并确认借出；归还时按单册条码查询、核对读者、选择正常/损坏/遗失情况并确认。
4. 读者登录查馆藏、预约、查看本人记录或续借；馆员查看候补并整理队列，领取时按保留单册办理借出。

部署详情见 [部署与备份](docs/DEPLOYMENT.md)，业务操作见 [操作手册](docs/USER_GUIDE.md)，接口及范围见 [接口与权限](docs/API.md)。公网部署需要 HTTPS 反向代理，设置 `COOKIE_SECURE=true`、限制暴露入口并配置域名、备份和监控；当前仓库提供本机 Compose 验收，不代表已完成公网域名部署或生产容量测试。

## 测试与验收

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 scripts/release-check.py
git diff --check
```

后端包括 37 项真实 MockMvc/JPA/Flyway 集成测试和 15 项业务边界测试，前端 11 项请求/错误/表单隔离测试。集成测试使用隔离 H2 MySQL 模式；完整部署验收使用全新 MySQL 8.4 数据卷。Docker 后端打包执行测试，不跳过。

仅在专用本机空库验收环境运行以下流程：会创建 TEST 账号与业务记录，空库检查失败就停止。私有凭证与验收快照写入已忽略的 `output/`。

```sh
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements-quality.txt
.venv/bin/black --check scripts
.venv/bin/python scripts/quality.py --base http://127.0.0.1:8127
.venv/bin/python scripts/verify-persistence.py --base http://127.0.0.1:8127
```

完整流程包含角色范围、读者隐私、重复条码、预约保留与顺序、取消推进、损坏/遗失、续借阻止、并发借出、归档、CSV 和历史保护。到期边界与逾期用受控时钟集成测试覆盖。重启后验证持久化；备份暂停指定项目后端写入，恢复仅允许全新独立项目，不覆盖已有资源。

```sh
.venv/bin/python scripts/backup.py --project librarydesk --output private-backups/librarydesk.zip
# Create .env.restore privately with a different WEB_PORT, e.g. 28127.
.venv/bin/python scripts/restore.py private-backups/librarydesk.zip --project librarydesk-restore --env-file .env.restore
.venv/bin/python scripts/verify-persistence.py --base http://127.0.0.1:28127
```

## 已知限制与第三方依赖

- 学习版适合小型馆藏验证。查询每类实体上限 10,000 条；部分统计/后台列表在内存计算，未经大馆容量和高并发压测，不提供生产可用承诺。
- 无匿名公共目录、馆际调拨、批量导入、MARC/Z39.50、ISBN 联网元数据、电子书、RFID、自助借还机或摄像头扫码集成。
- 无预约定时清理、邮件/短信/微信通知、SSO、罚款、支付、押金、节假日日历或自动打印。
- 原始借期和操作历史留存；展示书名和读者名使用当前目录，不是完整的历史姓名/书名快照。页面时间戳用浏览器时区，应还日按图书馆时区判断。
- 当前基本流通无需付费第三方服务。邮件、RFID 等没有已实现集成；商业授权不表示预留能力已经完成。第三方依赖保留各自版权，见 [第三方说明](docs/THIRD_PARTY.md)。

## 授权与联系知华科技

仅限个人学习、技术研究与非商业交流；未经上海如静知华信息科技有限公司书面授权不得商用。本许可不是 OSI 标准开源许可证，不是 MIT/Apache 免费商用协议。授权以 [LICENSE](LICENSE) 为准。

**知华科技（上海如静知华信息科技有限公司）** · [https://www.zhuatech.cn/](https://www.zhuatech.cn/)

**商业授权或深度定制开发请联系知华科技。** 支持商业授权、定制开发、部署与系统集成咨询。微信：`zhuatech`、`zhuatech2`。

<table><tr><td align="center"><img src="docs/images/wechat-zhuatech.png" alt="微信 zhuatech" height="200"><br>微信 zhuatech</td><td align="center"><img src="docs/images/wechat-zhuatech2.png" alt="微信 zhuatech2" height="200"><br>微信 zhuatech2</td></tr></table>
