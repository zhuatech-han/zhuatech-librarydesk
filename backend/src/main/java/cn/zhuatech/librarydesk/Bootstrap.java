// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库仅初始化目录和独立私有管理员。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  static final List<String> CODES =
      List.of(
          "catalog",
          "catalog_manage",
          "patrons",
          "circulation",
          "borrow",
          "reports",
          "users",
          "roles",
          "settings",
          "audit");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  /** 连接初始化私有环境配置。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${librarydesk.admin-username}") String u,
      @Value("${librarydesk.admin-password}") String p) {
    this.db = db;
    this.encoder = encoder;
    username = u;
    password = p;
  }

  /** 重启不覆盖已有身份或业务数据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalStateException("Invalid administrator name");
    for (var code : CODES) {
      var p = new Permission();
      p.code = code;
      p.name = code;
      db.save(p);
    }
    var d = new Department();
    d.name = "主图书馆 / Main library";
    d.zone = "Asia/Shanghai";
    db.save(d);
    var admin = role("管理员 / Administrator", "ALL", CODES);
    role(
        "馆员 / Librarian",
        "DEPARTMENT",
        List.of("catalog", "catalog_manage", "patrons", "circulation", "reports", "audit"));
    role("读者 / Patron", "ASSIGNED", List.of("catalog", "borrow"));
    role("只读查阅 / Viewer", "DEPARTMENT", List.of("catalog", "reports"));
    var a = new Account();
    a.username = username.toLowerCase(Locale.ROOT);
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.departmentId = d.id;
    a.roleId = admin.id;
    db.save(a);
    String[][] menus = {
      {"catalog", "馆藏目录", "Catalog", "catalog"},
      {"desk", "借还柜台", "Circulation desk", "circulation"},
      {"loans", "借阅记录", "Loans", "circulation"},
      {"holds", "预约队列", "Hold queue", "circulation"},
      {"my", "我的借阅", "My library", "borrow"},
      {"patrons", "读者管理", "Patrons", "patrons"},
      {"reports", "流通报表", "Reports", "reports"},
      {"users", "登录账号", "Accounts", "users"},
      {"roles", "角色与权限", "Roles & permissions", "roles"},
      {"settings", "图书馆与设置", "Libraries & settings", "settings"},
      {"audit", "操作记录", "Audit trail", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    String[][] categories = {
      {"GENERAL", "综合", "General"},
      {"FICTION", "文学", "Fiction"},
      {"SCIENCE", "科学", "Science"},
      {"BUSINESS", "管理", "Business"}
    };
    for (var v : categories) {
      var x = new DictionaryEntry();
      x.type = "CATEGORY";
      x.code = v[0];
      x.name = v[1];
      x.nameEn = v[2];
      db.save(x);
    }
    String[][] settings = {
      {"loan_days", "14"},
      {"max_loans", "5"},
      {"max_renewals", "2"},
      {"hold_hours", "48"},
      {"max_holds", "3"}
    };
    for (var v : settings) {
      var x = new SystemSetting();
      x.code = v[0];
      x.value = v[1];
      db.save(x);
    }
  }

  private AccessRole role(String n, String scope, List<String> codes) {
    var v = new AccessRole();
    v.name = n;
    v.scope = scope;
    v.permissions.addAll(codes);
    return db.save(v);
  }
}
