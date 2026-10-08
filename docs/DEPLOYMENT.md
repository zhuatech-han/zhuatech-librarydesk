# LibraryDesk 部署与备份 / Deployment and backup

知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/) · 商业授权/定制微信 zhuatech、zhuatech2。

ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · [han@zhuatech.cn](mailto:han@zhuatech.cn), [jack@zhuatech.cn](mailto:jack@zhuatech.cn), [WhatsApp](https://wa.me/8617521234993).

## 本机部署 / Local deployment

按照根目录 README 生成私有 .env、校验 Compose，再 build/up。MySQL 无主机端口，后端无主机端口，Nginx 默认仅 127.0.0.1:8127。应用与前端容器均非 root 运行，健康、登录和 CSRF 引导允许匿名访问；业务接口需要登录。构建执行完整测试。不要使用 down --volumes 作为日常停机：会删除指定数据库卷。

Follow root README: private .env, Compose validation, build/up. MySQL and backend have no published host port; Nginx defaults to localhost:8127. Backend/frontend run as non-root. Health, sign-in and CSRF bootstrap allow anonymous access; business endpoints require login. Builds run tests. Do not use down --volumes for routine shutdown: it removes the named database volume.

## 公网部署 / Internet deployment

在经过授权的服务器上配置 HTTPS 反向代理、域名及证书，启用 COOKIE_SECURE，保留同源路由和安全头。后端不公开；数据库最小权限、TLS及访问限制独立配置。外部数据库 JDBC 用 sslMode=VERIFY_IDENTITY 并安装正确 CA。创建账号、日常备份、监控健康及审计，先做容量与恢复演练。单实例学习版未做生产容量、硬件兼容或多节点部署验收。

Use an authorized server with HTTPS reverse proxy, domain/certificate, secure cookies, same-origin routing and security headers. Do not expose the backend. Configure database privileges, TLS and network limits. External JDBC needs VERIFY_IDENTITY and a trusted CA. Operate accounts, backups, health/audit monitoring and capacity/recovery drills. Single-instance learning edition has no production capacity, hardware or multi-node acceptance.

## 备份与恢复 / Backup and restore

scripts/backup.py 使用 --project 明确指定 Compose 项目，暂停其后端，单事务 mysqldump 后重启；私有 ZIP 包含数据库 SQL（含密码散列）与 SHA256 manifest。文件0600，不得公开。脚本仅支持内置 Compose 数据库，没有附件存储。

scripts/restore.py 只接受可信本机 LibraryDesk 备份，先校验成员、大小、产品和 SHA，再创建不存在的独立项目；已有容器/卷/网络拒绝覆盖。私有 .env.restore 指定不同端口、随机数据库口令、localhost绑定。恢复后核对业务快照/CSV、健康、登录和迁移；原数据库管理员保持原密码，恢复环境里的 ADMIN_PASSWORD 不会重设它。

Backup names the exact project, stops its backend, makes a transactional dump and restarts it. A private 0600 ZIP contains SQL (including password hashes) and SHA256 manifest. No file attachments are stored. Restore validates trusted local archives and creates only an absent isolated project; existing containers/volumes/networks are rejected. Use a private restore environment with another port, random database credentials and localhost binding. Verify records/CSV, health, sign-in and migrations. Restored administrator credentials remain those in the original database, not the new bootstrap variable.

重启用 docker compose -p librarydesk restart，保留命名卷。升级先备份，再审核新增迁移并构建、健康和业务回归验证。Restart preserves named volumes. Upgrade only after backup and reviewed new migrations, image builds, health and business regression checks.

仅限个人学习交流，商用须公司书面授权。Non-commercial learning edition; commercial use requires written authorization.
