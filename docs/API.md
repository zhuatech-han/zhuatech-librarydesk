# LibraryDesk 接口与权限 / API and scopes

知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/) · 商业授权/定制微信 zhuatech、zhuatech2。

ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · [han@zhuatech.cn](mailto:han@zhuatech.cn), [jack@zhuatech.cn](mailto:jack@zhuatech.cn), [WhatsApp](https://wa.me/8617521234993).

同源 /api，所有写操作取 /api/auth/csrf 返回的 header/token 并携带会话 Cookie。除 csrf/login 外均需要登录。变更已有记录带当前 version，失败不自动重试。返回错误 code；400 输入、401 会话、403 权限/范围、404 不存在、409 状态/版本冲突、413 查询上限。

Same-origin /api. Writes need the CSRF header/token and session cookie. Except csrf/login, endpoints require authentication. Updates carry the current version. No automatic replay. Errors return code: 400 input, 401 session, 403 permission/scope, 404 missing, 409 state/version conflict, 413 query limit.

| Endpoint | Permission / behavior |
| --- | --- |
| GET /auth/me, POST /auth/logout, /auth/password | Current authenticated identity; own password |
| GET /options, /books, /books/{id} | catalog; archived titles need catalog_manage |
| POST/PUT/DELETE /books | catalog_manage; department/version; referenced history protected |
| POST /books/{id}/copies, PUT/DELETE /copies/{id} | catalog_manage; stable barcode; busy state protected |
| GET/POST/PUT/DELETE /patrons | patrons; library scope, stable account/card identity |
| GET /my | borrow; only bound own borrower card |
| GET /lookup?barcode=... | circulation; lookup has no writes |
| POST /checkout | circulation; copyId, patronId, version |
| POST /loans/{id}/return | circulation; version, condition AVAILABLE/DAMAGED/LOST, note |
| POST /loans/{id}/renew | borrow or circulation; own/scoped loan, version |
| POST /holds | borrow or circulation; bookId; staff may specify patronId |
| POST /holds/{id}/cancel | borrow or circulation; own/scoped hold, version, reason |
| POST /holds/process | circulation; process visible libraries |
| GET /records/loans, /records/holds | borrow or circulation; mine, q, state, page, size, sort |
| GET /reports, /reports/export | reports; scoped metrics and UTF-8 CSV |
| GET /audit | audit; scoped, ASSIGNED only own actor |
| GET /admin/options, /admin/{kind}, POST/PUT /admin/{kind} | users / roles / settings with ALL scope |

kind 固定为 users、roles、permissions、departments、dictionaries、settings、menus。账号/角色/图书馆版本检查；权限/参数/菜单代码固定。分类字典仅 CATEGORY。读者不能伪造 patronId 为别人预约；角色 ASSIGNED 只允许本人流通；ALL 并不绕过缺失的业务权限。后台 users 响应不包含密码散列。

Admin kinds are fixed to users, roles, permissions, departments, dictionaries, settings and menus. Account/role/library updates validate versions; permission/parameter/menu codes stay fixed. Only CATEGORY dictionaries are supported. Patrons cannot forge another patronId. ASSIGNED circulation is own-only; ALL scope does not bypass permission checks. Account responses omit password hashes.

无批量导入接口、匿名目录、第三方通知、支付或硬件集成。No bulk import, anonymous catalog, external notifications, payments or hardware integration endpoints.

仅限个人学习交流，商用须公司书面授权。Non-commercial learning edition; commercial use requires written authorization.
