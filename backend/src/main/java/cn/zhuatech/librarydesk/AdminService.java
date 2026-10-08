// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统目录管理，保留最后一个完整管理员并防止并发覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class AdminService {
  final Store db;
  final AccessService access;
  final BCryptPasswordEncoder encoder;

  /** 连接安全目录和密码服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public AdminService(Store db, AccessService access, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.access = access;
    this.encoder = encoder;
  }

  /** 强密码尊重BCrypt的72字节限制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void validatePassword(String s) {
    if (s == null
        || s.getBytes(StandardCharsets.UTF_8).length < 12
        || s.getBytes(StandardCharsets.UTF_8).length > 72
        || !s.matches("(?s).*[a-z].*")
        || !s.matches("(?s).*[A-Z].*")
        || !s.matches("(?s).*[0-9].*")
        || s.chars().anyMatch(Character::isISOControl)) throw new Problem(400, "PASSWORD_WEAK");
  }

  String permission(String k) {
    return switch (k) {
      case "users" -> "users";
      case "roles", "permissions" -> "roles";
      case "departments", "menus", "dictionaries", "settings" -> "settings";
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  void require(String k) {
    access.require(permission(k));
    if (!access.role().scope.equals("ALL")) throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 统一写入锁用于低容量部署，避免最后管理员并发竞争。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void lock() {
    db.lock(Department.class, 1L);
  }

  /** 读取目录；账号密码有@JsonIgnore，永不返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object read(String k) {
    require(k);
    return switch (k) {
      case "users" -> db.all(Account.class);
      case "roles" -> db.all(AccessRole.class);
      case "permissions" -> db.all(Permission.class);
      case "departments" -> db.all(Department.class);
      case "menus" -> db.all(NavMenu.class);
      case "dictionaries" -> db.all(DictionaryEntry.class);
      case "settings" -> db.all(SystemSetting.class);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 账号配置所需的最小目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> options() {
    require("users");
    return Map.of("roles", db.all(AccessRole.class), "departments", db.all(Department.class));
  }

  /** 新增或完整更新目录；记录不可变身份、版本与审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String k, Long id, Map<String, Object> b) {
    require(k);
    lock();
    Object out;
    switch (k) {
      case "users" -> {
        var a = id == null ? new Account() : db.get(Account.class, id);
        if (id != null) Rules.check(a.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
        String u = Rules.text(b.get("username"), 60, true).toLowerCase(Locale.ROOT);
        Rules.check(u.matches("[a-z0-9_.-]{3,60}"), "INVALID_INPUT");
        if (id != null) Rules.check(a.username.equals(u), "IDENTITY_LOCKED");
        var role = db.get(AccessRole.class, Rules.id(b.get("roleId")));
        var dept = db.get(Department.class, Rules.id(b.get("departmentId")));
        Rules.check(dept.enabled, "DEPARTMENT_DISABLED");
        if (id != null && !Objects.equals(a.departmentId, dept.id))
          Rules.check(
              db.query(Patron.class, "from Patron where accountId=?1", id).isEmpty(),
              "ACCOUNT_ASSIGNED");
        String pw = Objects.toString(b.get("password"), "");
        if (id == null || !pw.isEmpty()) {
          validatePassword(pw);
          a.passwordHash = encoder.encode(pw);
        }
        a.username = u;
        a.displayName = Rules.text(b.get("displayName"), 120, true);
        a.roleId = role.id;
        a.departmentId = dept.id;
        a.enabled = Rules.flag(b.get("enabled"));
        if (id == null) db.save(a);
        else a.version++;
        db.flush();
        lastAdmin();
        out = a;
      }
      case "roles" -> {
        var r = id == null ? new AccessRole() : db.get(AccessRole.class, id);
        if (id != null) Rules.check(r.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
        String scope = Rules.text(b.get("scope"), 20, true);
        Rules.check(Set.of("ALL", "DEPARTMENT", "ASSIGNED").contains(scope), "INVALID_INPUT");
        if (!(b.get("permissions") instanceof List<?> list))
          throw new Problem(400, "INVALID_INPUT");
        Set<String> codes = new HashSet<>();
        for (var x : list) {
          String c = Rules.text(x, 60, true);
          Rules.check(Bootstrap.CODES.contains(c), "UNKNOWN_PERMISSION");
          codes.add(c);
        }
        r.name = Rules.text(b.get("name"), 120, true);
        r.scope = scope;
        r.permissions.clear();
        r.permissions.addAll(codes);
        if (id == null) db.save(r);
        else r.version++;
        db.flush();
        lastAdmin();
        out = r;
      }
      case "departments" -> {
        var d = id == null ? new Department() : db.get(Department.class, id);
        if (id != null) Rules.check(d.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
        d.name = Rules.text(b.get("name"), 120, true);
        d.zone = Rules.text(b.get("zone"), 80, true);
        ZoneId.of(d.zone);
        d.enabled = Rules.flag(b.get("enabled"));
        if (id == null) db.save(d);
        else d.version++;
        db.flush();
        lastAdmin();
        out = d;
      }
      case "dictionaries" -> {
        var d = id == null ? new DictionaryEntry() : db.get(DictionaryEntry.class, id);
        String code = Rules.text(b.get("code"), 60, true);
        Rules.check(code.matches("[A-Z0-9_]{2,60}"), "INVALID_INPUT");
        if (id != null) Rules.check(code.equals(d.code), "IDENTITY_LOCKED");
        d.type = "CATEGORY";
        d.code = code;
        d.name = Rules.text(b.get("name"), 120, true);
        d.nameEn = Rules.text(b.get("nameEn"), 120, true);
        d.enabled = Rules.flag(b.get("enabled"));
        if (id == null) db.save(d);
        out = d;
      }
      case "menus" -> {
        Rules.check(id != null, "SYSTEM_DIRECTORY_FIXED");
        var m = db.get(NavMenu.class, id);
        Rules.check(
            m.code.equals(b.get("code")) && m.permissionCode.equals(b.get("permissionCode")),
            "IDENTITY_LOCKED");
        m.name = Rules.text(b.get("name"), 120, true);
        m.nameEn = Rules.text(b.get("nameEn"), 120, true);
        m.position = Rules.integer(b.get("position"), 0, 100);
        m.enabled = Rules.flag(b.get("enabled"));
        out = m;
      }
      case "permissions" -> {
        Rules.check(id != null, "SYSTEM_DIRECTORY_FIXED");
        var x = db.get(Permission.class, id);
        Rules.check(x.code.equals(b.get("code")), "IDENTITY_LOCKED");
        x.name = Rules.text(b.get("name"), 120, true);
        out = x;
      }
      case "settings" -> {
        Rules.check(id != null, "SYSTEM_DIRECTORY_FIXED");
        var x = db.get(SystemSetting.class, id);
        Rules.check(x.code.equals(b.get("code")), "IDENTITY_LOCKED");
        String v = Rules.text(b.get("value"), 40, true);
        switch (x.code) {
          case "loan_days" -> Rules.integer(v, 1, 365);
          case "max_loans", "max_holds" -> Rules.integer(v, 1, 50);
          case "max_renewals" -> Rules.integer(v, 0, 20);
          case "hold_hours" -> Rules.integer(v, 1, 720);
          default -> throw new Problem(400, "INVALID_INPUT");
        }
        x.value = v;
        out = x;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.audit(
        "ADMIN_" + k.toUpperCase(Locale.ROOT),
        id == null ? "NEW" : id,
        access.current().departmentId);
    return out;
  }

  void lastAdmin() {
    Rules.check(
        db.all(Account.class).stream()
            .anyMatch(
                a ->
                    a.enabled
                        && db.get(Department.class, a.departmentId).enabled
                        && db.get(AccessRole.class, a.roleId).scope.equals("ALL")
                        && db.get(AccessRole.class, a.roleId)
                            .permissions
                            .containsAll(Bootstrap.CODES)),
        "LAST_ADMIN");
  }

  /** 获取经过校验的参数，不执行动态SQL。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public int setting(String code) {
    return Integer.parseInt(
        db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value);
  }
}
