// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 书目、单册与读者目录管理，业务身份稳定并保留引用历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class CatalogService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;

  /** 连接持久化、实时权限和业务时钟。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public CatalogService(Store d, AccessService a, AdminService m, Clock c) {
    db = d;
    access = a;
    admin = m;
    clock = c;
  }

  void version(long current, Map<String, Object> b) {
    Rules.check(current == Rules.id(b.get("version")), "VERSION_CONFLICT");
  }

  /** 最小作用域选项；仅馆员可读取读者候选，不返回密码或角色配置。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("catalog");
    var out = new LinkedHashMap<String, Object>();
    out.put(
        "departments",
        db.all(Department.class).stream()
            .filter(d -> d.enabled && access.department(d.id))
            .toList());
    out.put(
        "categories",
        db.query(DictionaryEntry.class, "from DictionaryEntry where type='CATEGORY'").stream()
            .filter(x -> x.enabled)
            .toList());
    if (access.has("patrons"))
      out.put(
          "accounts",
          db.all(Account.class).stream()
              .filter(
                  a ->
                      access.department(a.departmentId)
                          && a.enabled
                          && db.get(AccessRole.class, a.roleId).permissions.contains("borrow"))
              .map(
                  a ->
                      Map.of(
                          "id", a.id, "displayName", a.displayName, "departmentId", a.departmentId))
              .toList());
    return out;
  }

  /** 搜索、过滤和分页展示可见书目；读者看不到停用或外馆书目。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(
      String query, String category, String status, int page, int size, String sort) {
    access.require("catalog");
    Rules.integer(page, 1, 100000);
    Rules.integer(size, 1, 100);
    String q = Rules.text(query, 200, false).toLowerCase(Locale.ROOT);
    Rules.text(category, 60, false);
    if (!Set.of("title", "latest").contains(sort)
        || !Set.of("", "active", "archived").contains(status))
      throw new Problem(400, "INVALID_INPUT");
    var rows =
        db.all(Book.class).stream()
            .filter(
                b ->
                    access.department(b.departmentId) && (b.active || access.has("catalog_manage")))
            .filter(b -> category.isEmpty() || b.category.equals(category))
            .filter(b -> status.isEmpty() || b.active == status.equals("active"))
            .filter(
                b -> (b.title + " " + b.author + " " + b.isbn).toLowerCase(Locale.ROOT).contains(q))
            .sorted(
                sort.equals("title")
                    ? Comparator.comparing((Book b) -> b.title).thenComparing(b -> b.id)
                    : Comparator.comparing((Book b) -> b.id).reversed())
            .toList();
    return Map.of(
        "items",
        rows.stream().skip((long) (page - 1) * size).limit(size).map(this::summary).toList(),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 书目摘要从单册实时计算，不包含其他读者身份。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> summary(Book b) {
    var copies = db.query(BookCopy.class, "from BookCopy where bookId=?1", b.id);
    return Map.of(
        "book",
        b,
        "copies",
        copies.size(),
        "available",
        copies.stream().filter(c -> c.status.equals("AVAILABLE")).count(),
        "waiting",
        db.query(BookHold.class, "from BookHold where bookId=?1 and status='WAITING'", b.id)
            .size());
  }

  /** 读取单册信息与属于自己的预约；馆员才可读取全部馆内预约和事件。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    var b = db.get(Book.class, id);
    access.library(b.departmentId, "catalog");
    if (!b.active && !access.has("catalog_manage")) throw new Problem(404, "NOT_FOUND");
    var out = new LinkedHashMap<String, Object>(summary(b));
    out.put("copies", db.query(BookCopy.class, "from BookCopy where bookId=?1", id));
    out.put(
        "holds",
        db.query(BookHold.class, "from BookHold where bookId=?1", id).stream()
            .filter(h -> access.patron(db.get(Patron.class, h.patronId)))
            .toList());
    if (access.has("circulation"))
      out.put(
          "events",
          db
              .query(
                  CirculationEvent.class,
                  "from CirculationEvent where bookId=?1 order by id desc",
                  id)
              .stream()
              .filter(
                  e ->
                      !access.role().scope.equals("ASSIGNED")
                          || (e.patronId != null
                              && db.get(Patron.class, e.patronId)
                                  .accountId
                                  .equals(access.current().id)))
              .toList());
    return out;
  }

  /** 保存书目元数据；有流通历史时只可归档，不删除。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Book saveBook(Long id, Map<String, Object> b) {
    access.require("catalog_manage");
    admin.lock();
    var x = id == null ? new Book() : db.get(Book.class, id);
    if (id == null) {
      x.departmentId = Rules.id(b.get("departmentId"));
      x.createdAt = clock.instant();
    } else {
      version(x.version, b);
      Rules.check(
          Objects.equals(x.departmentId, Rules.id(b.get("departmentId"))), "IDENTITY_LOCKED");
      x.version++;
    }
    access.library(x.departmentId, "catalog_manage");
    Rules.check(db.get(Department.class, x.departmentId).enabled, "DEPARTMENT_DISABLED");
    x.title = Rules.text(b.get("title"), 200, true);
    x.author = Rules.text(b.get("author"), 160, true);
    x.isbn = LibraryPolicy.isbn(b.get("isbn"));
    x.category = Rules.text(b.get("category"), 60, true);
    Rules.check(
        !db.query(
                DictionaryEntry.class,
                "from DictionaryEntry where type='CATEGORY' and code=?1 and enabled=true",
                x.category)
            .isEmpty(),
        "CATEGORY_DISABLED");
    x.language = Rules.text(b.get("language"), 40, true);
    Rules.check(x.language.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{2,8})*"), "LANGUAGE_INVALID");
    x.description = Rules.paragraph(b.get("description"), 2000, false);
    x.active = Rules.flag(b.get("active"));
    if (!x.isbn.isEmpty())
      Rules.check(
          db
              .query(
                  Book.class, "from Book where departmentId=?1 and isbn=?2", x.departmentId, x.isbn)
              .stream()
              .noneMatch(v -> !Objects.equals(v.id, id)),
          "ISBN_DUPLICATE");
    if (!x.active)
      Rules.check(
          db.query(
                  BookHold.class,
                  "from BookHold where bookId=?1 and status in ('WAITING','READY')",
                  id == null ? -1L : id)
              .isEmpty(),
          "ACTIVE_HOLDS");
    if (id == null) db.save(x);
    access.audit(id == null ? "BOOK_CREATE" : "BOOK_UPDATE", x.id, x.departmentId);
    return x;
  }

  /** 删除完全未录入单册和流通的书目；外键继续保护历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteBook(Long id, long v) {
    admin.lock();
    var x = db.get(Book.class, id);
    access.library(x.departmentId, "catalog_manage");
    Rules.check(x.version == v, "VERSION_CONFLICT");
    Rules.check(
        db.query(BookCopy.class, "from BookCopy where bookId=?1", id).isEmpty()
            && db.query(BookHold.class, "from BookHold where bookId=?1", id).isEmpty(),
        "RECORD_REFERENCED");
    access.audit("BOOK_DELETE", id, x.departmentId);
    db.delete(x);
  }

  /** 新增唯一条码单册；同书可多册。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BookCopy createCopy(Long bookId, Map<String, Object> b) {
    admin.lock();
    var book = db.get(Book.class, bookId);
    access.library(book.departmentId, "catalog_manage");
    Rules.check(book.active, "BOOK_ARCHIVED");
    var c = new BookCopy();
    c.bookId = bookId;
    c.barcode = LibraryPolicy.code(b.get("barcode"));
    Rules.check(
        db.query(BookCopy.class, "from BookCopy where barcode=?1", c.barcode).isEmpty(),
        "BARCODE_DUPLICATE");
    c.shelf = Rules.text(b.get("shelf"), 80, false);
    c.note = Rules.paragraph(b.get("note"), 500, false);
    c.createdAt = clock.instant();
    db.save(c);
    access.audit("COPY_CREATE", c.id, book.departmentId);
    return c;
  }

  /** 维护单册书架和异常状态；借出或待领不能绕过流通接口改状态。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BookCopy editCopy(Long id, Map<String, Object> b) {
    admin.lock();
    var c = db.get(BookCopy.class, id);
    var book = db.get(Book.class, c.bookId);
    access.library(book.departmentId, "catalog_manage");
    version(c.version, b);
    String status = Rules.text(b.get("status"), 20, true);
    Rules.check(
        Set.of("AVAILABLE", "DAMAGED", "LOST", "WITHDRAWN").contains(status)
            || status.equals(c.status),
        "INVALID_INPUT");
    if (!status.equals(c.status)) {
      Rules.check(!Set.of("ON_LOAN", "HELD").contains(c.status), "COPY_BUSY");
      Rules.check(!Set.of("ON_LOAN", "HELD").contains(status), "COPY_BUSY");
      Rules.paragraph(b.get("note"), 500, true);
    }
    c.status = status;
    c.shelf = Rules.text(b.get("shelf"), 80, false);
    c.note = Rules.paragraph(b.get("note"), 500, false);
    c.version++;
    access.audit("COPY_UPDATE", id, book.departmentId);
    return c;
  }

  /** 删除完全没有流通历史的单册；其余使用保留历史的退出状态。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteCopy(Long id, long v) {
    admin.lock();
    var c = db.get(BookCopy.class, id);
    var b = db.get(Book.class, c.bookId);
    access.library(b.departmentId, "catalog_manage");
    Rules.check(c.version == v, "VERSION_CONFLICT");
    Rules.check(
        c.status.equals("AVAILABLE")
            && db.query(Loan.class, "from Loan where copyId=?1", id).isEmpty()
            && db.query(BookHold.class, "from BookHold where copyId=?1", id).isEmpty(),
        "RECORD_REFERENCED");
    access.audit("COPY_DELETE", id, b.departmentId);
    db.delete(c);
  }

  /** 读者列表仅馆员可见，借阅资格与账号权限实时核对。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object patrons() {
    access.require("patrons");
    return db.all(Patron.class).stream()
        .filter(p -> access.department(p.departmentId))
        .map(
            p ->
                Map.of(
                    "patron",
                    p,
                    "name",
                    db.get(Account.class, p.accountId).displayName,
                    "accountEnabled",
                    db.get(Account.class, p.accountId).enabled,
                    "openLoans",
                    db.query(Loan.class, "from Loan where patronId=?1 and status='OPEN'", p.id)
                        .size()))
        .toList();
  }

  /** 开办或修改借阅证；账号和馆身份不可改，停用保留历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Patron savePatron(Long id, Map<String, Object> b) {
    access.require("patrons");
    admin.lock();
    var p = id == null ? new Patron() : db.get(Patron.class, id);
    var a = db.get(Account.class, Rules.id(b.get("accountId")));
    if (id == null) {
      p.accountId = a.id;
      p.departmentId = a.departmentId;
      p.cardNo = LibraryPolicy.code(b.get("cardNo"));
      p.createdAt = clock.instant();
      Rules.check(
          db.query(Patron.class, "from Patron where accountId=?1 or cardNo=?2", a.id, p.cardNo)
              .isEmpty(),
          "PATRON_DUPLICATE");
    } else {
      version(p.version, b);
      Rules.check(
          p.accountId.equals(a.id) && p.cardNo.equals(LibraryPolicy.code(b.get("cardNo"))),
          "IDENTITY_LOCKED");
      p.version++;
    }
    access.library(p.departmentId, "patrons");
    p.active = Rules.flag(b.get("active"));
    if (id == null || p.active)
      Rules.check(
          a.enabled && db.get(AccessRole.class, a.roleId).permissions.contains("borrow"),
          "ACCOUNT_INELIGIBLE");
    p.note = Rules.paragraph(b.get("note"), 500, false);
    if (id == null) db.save(p);
    access.audit("PATRON_SAVE", p.id, p.departmentId);
    return p;
  }

  /** 删除未借阅和预约过的借阅证；已有历史使用停用。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deletePatron(Long id, long v) {
    admin.lock();
    var p = db.get(Patron.class, id);
    access.library(p.departmentId, "patrons");
    Rules.check(p.version == v, "VERSION_CONFLICT");
    Rules.check(
        db.query(Loan.class, "from Loan where patronId=?1", id).isEmpty()
            && db.query(BookHold.class, "from BookHold where patronId=?1", id).isEmpty(),
        "RECORD_REFERENCED");
    access.audit("PATRON_DELETE", id, p.departmentId);
    db.delete(p);
  }
}
