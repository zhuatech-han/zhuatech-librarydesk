# LibraryDesk 操作手册 / User guide

知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/) · 商业授权/定制微信 zhuatech、zhuatech2。

ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · [han@zhuatech.cn](mailto:han@zhuatech.cn), [jack@zhuatech.cn](mailto:jack@zhuatech.cn), [WhatsApp](https://wa.me/8617521234993).

## 管理员 / Administrator

先创建馆员（librarian）和读者（patron）账号；密码由部署管理员设置，角色和权限不可只靠界面隐藏。全部馆范围才可管理账号、角色和系统设置。角色 ALL、DEPARTMENT、ASSIGNED 分别为全部馆、本馆、本人流通。分类与菜单可启停，代码固定；应保留可访问的业务入口。时区填写 IANA 名称，例如 Asia/Shanghai、Europe/Berlin。

Create librarian and patron accounts first. Deployment administrators set passwords. Server permission checks are independent of hidden menus. Identity/role/settings management needs ALL scope. Scope values are ALL, DEPARTMENT and ASSIGNED (own circulation). Category/menu codes stay fixed. Keep the necessary enabled business entry points. Libraries use IANA timezones.

## 馆员 / Librarian

书目代表同一图书，单册代表实际一本。ISBN 可不填；每本使用全局唯一条码。借阅证关联已有启用且有 borrow 权限的登录账号。借阅证号、账号和单册条码建立后不可更换；需新的身份请建立新记录。

柜台输入条码按 Enter 仅查询。可借单册选择读者并确认借出；待领单册只能借给保留的读者。归还核对借阅人和应还日，选择正常、损坏或遗失；损坏/遗失必须说明，保留历史。修复单册后编辑为“可借”并写说明，再整理队列。

Holds are title-level; copy barcodes identify actual physical copies. ISBN is optional. Register cards for enabled borrowing accounts. Account/card/copy identities cannot be replaced after creation. Enter only looks up the copy; confirm check-out/check-in separately. Held copies belong to the current ready patron. Damage/loss needs a note. Repair a copy to AVAILABLE with a note and process the queue afterward.

## 读者 / Patron

读者只能看本馆启用书目及本人流通。点击预约后在“我的借阅—我的预约”查看候补位置或单册条码和领取截止，带借阅证到馆由馆员办理。取消会让后续读者获得机会。没有借阅证时页面提示联系馆员；有逾期或其他读者预约时续借会被拒绝并给出原因。

Patrons see active titles in their library and their own circulation records. Place a hold, then view queue position or reserved barcode/deadline in My borrowing. Collect through staff before the deadline. Cancellation can promote the next eligible patron. No-card accounts get a clear prompt. Overdue loans or another patron’s hold block renewal with an explicit reason.

## 错误与恢复 / Errors and recovery

记录版本冲突时刷新核对，不盲目重试；网络中断提示“提交结果未知”时先查询记录再决定，系统不自动重复写操作。停用保留历史，不删除借阅、预约或审计。没有自动定时清理或通知，馆员必须日常“整理队列”。

Refresh and inspect after a version conflict. An interrupted write has an unknown result: inspect records before deciding; writes are never automatically replayed. Disabling preserves history. Loans/holds/audits have no delete API. There is no scheduled processing or notification; staff must routinely process queues.

仅限个人学习交流，商用须公司书面授权。Non-commercial learning edition; commercial use requires written authorization.
