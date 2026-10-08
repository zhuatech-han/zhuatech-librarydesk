// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 按唯一单册借还、自然日续借和书目FIFO候补；所有写入在统一数据库锁内。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class CirculationService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;

  /** 连接流通状态、身份和可测试时钟。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public CirculationService(Store d, AccessService a, AdminService m, Clock c) {
    db = d;
    access = a;
    admin = m;
    clock = c;
  }

  LocalDate today(Long dept) {
    return LocalDate.ofInstant(clock.instant(), ZoneId.of(db.get(Department.class, dept).zone));
  }

  boolean eligible(Patron p) {
    var a = db.get(Account.class, p.accountId);
    return p.active
        && a.enabled
        && db.get(Department.class, p.departmentId).enabled
        && db.get(AccessRole.class, a.roleId).permissions.contains("borrow");
  }

  void ready(Patron p) {
    Rules.check(eligible(p), "PATRON_DISABLED");
    Rules.check(
        db.query(Loan.class, "from Loan where patronId=?1 and status='OPEN'", p.id).stream()
            .noneMatch(l -> LibraryPolicy.overdue(l.dueDate, today(l.departmentId))),
        "PATRON_OVERDUE");
  }

  Patron own() {
    access.require("borrow");
    var rows = db.query(Patron.class, "from Patron where accountId=?1", access.current().id);
    if (rows.isEmpty()) throw new Problem(409, "PATRON_NOT_REGISTERED");
    return rows.getFirst();
  }

  void patronScope(Patron p) {
    if (!access.patron(p)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  void event(String action, Book b, BookCopy c, Patron p, Loan l, BookHold h, String note) {
    var e = new CirculationEvent();
    e.departmentId = b.departmentId;
    e.bookId = b.id;
    e.copyId = c == null ? null : c.id;
    e.patronId = p == null ? null : p.id;
    e.loanId = l == null ? null : l.id;
    e.holdId = h == null ? null : h.id;
    e.action = action;
    e.note = note;
    e.actorId = access.current().id;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, e.id, b.departmentId);
  }

  void closeHold(BookHold h, String state, String reason) {
    if (h.copyId != null) {
      var c = db.get(BookCopy.class, h.copyId);
      if (c.status.equals("HELD")) {
        c.status = "AVAILABLE";
        c.version++;
      }
    }
    h.status = state;
    h.reason = reason;
    h.closedAt = clock.instant();
    h.version++;
    var b = db.get(Book.class, h.bookId);
    event(
        "HOLD_" + state,
        b,
        h.copyId == null ? null : db.get(BookCopy.class, h.copyId),
        db.get(Patron.class, h.patronId),
        null,
        h,
        reason);
  }

  /** 清理已过取书期限和停用读者，再按创建时刻及ID稳定先后分配；无GET副作用。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void processBook(Book b) {
    var active =
        db.query(
            BookHold.class,
            "from BookHold where bookId=?1 and status in ('WAITING','READY') order by createdAt,id",
            b.id);
    for (var h : active) {
      if (!eligible(db.get(Patron.class, h.patronId))) closeHold(h, "CANCELLED", "PATRON_DISABLED");
      else if (h.status.equals("READY") && !clock.instant().isBefore(h.expiresAt))
        closeHold(h, "EXPIRED", "PICKUP_EXPIRED");
    }
    if (!b.active) return;
    var copies =
        db.query(
            BookCopy.class,
            "from BookCopy where bookId=?1 and status='AVAILABLE' order by id",
            b.id);
    int n = 0;
    for (var h : active) {
      if (!h.status.equals("WAITING")) continue;
      if (n >= copies.size()) break;
      var p = db.get(Patron.class, h.patronId);
      if (db.query(Loan.class, "from Loan where patronId=?1 and status='OPEN'", p.id).stream()
          .anyMatch(l -> LibraryPolicy.overdue(l.dueDate, today(l.departmentId)))) continue;
      var c = copies.get(n++);
      c.status = "HELD";
      c.version++;
      h.copyId = c.id;
      h.status = "READY";
      h.readyAt = clock.instant();
      h.expiresAt = h.readyAt.plusSeconds(admin.setting("hold_hours") * 3600L);
      h.version++;
      event("HOLD_READY", b, c, p, null, h, "");
    }
  }

  /** 馆员显式整理可见队列，记录过期释放，不依赖用户一直开着浏览器。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object process() {
    access.require("circulation");
    admin.lock();
    for (var b : db.all(Book.class)) if (access.department(b.departmentId)) processBook(b);
    return Map.of("ok", true);
  }

  /** 读者自己或馆员代办预约；持有人及图书馆范围绝不能由客户端绕过。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BookHold hold(Map<String, Object> b) {
    admin.lock();
    var book = db.get(Book.class, Rules.id(b.get("bookId")));
    access.library(book.departmentId, "catalog");
    var p =
        access.has("circulation") && b.get("patronId") != null
            ? db.get(Patron.class, Rules.id(b.get("patronId")))
            : own();
    patronScope(p);
    if (!access.has("circulation")
        && b.get("patronId") != null
        && !p.id.equals(Rules.id(b.get("patronId")))) throw new Problem(403, "OUT_OF_SCOPE");
    Rules.check(p.departmentId.equals(book.departmentId), "LIBRARY_MISMATCH");
    ready(p);
    Rules.check(book.active, "BOOK_ARCHIVED");
    processBook(book);
    Rules.check(
        db.query(
                BookHold.class,
                "from BookHold where bookId=?1 and patronId=?2 and status in ('WAITING','READY')",
                book.id,
                p.id)
            .isEmpty(),
        "HOLD_DUPLICATE");
    Rules.check(
        db.query(
                    BookHold.class,
                    "from BookHold where patronId=?1 and status in ('WAITING','READY')",
                    p.id)
                .size()
            < admin.setting("max_holds"),
        "HOLD_LIMIT");
    Rules.check(
        db.query(
                Loan.class,
                "from Loan l where l.patronId=?1 and l.status='OPEN' and l.copyId in (select c.id from BookCopy c where c.bookId=?2)",
                p.id,
                book.id)
            .isEmpty(),
        "ALREADY_BORROWED");
    var h = new BookHold();
    h.bookId = book.id;
    h.departmentId = book.departmentId;
    h.patronId = p.id;
    h.createdAt = clock.instant();
    db.save(h);
    event("HOLD_CREATED", book, null, p, null, h, "");
    processBook(book);
    return h;
  }

  /** 持有人取消或馆员带原因取消预约；重排不会跳过早期候补。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BookHold cancelHold(Long id, Map<String, Object> b) {
    admin.lock();
    var h = db.get(BookHold.class, id);
    var p = db.get(Patron.class, h.patronId);
    patronScope(p);
    if (!access.has("circulation")) access.require("borrow");
    Rules.check(h.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
    Rules.check(Set.of("WAITING", "READY").contains(h.status), "HOLD_CLOSED");
    String reason = Rules.paragraph(b.get("reason"), 1000, true);
    closeHold(h, "CANCELLED", reason);
    processBook(db.get(Book.class, h.bookId));
    return h;
  }

  /** 柜台按单册借出；保护已预约单册、借阅上限与逾期，保存借期策略快照。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Loan checkout(Map<String, Object> b) {
    access.require("circulation");
    admin.lock();
    var c = db.get(BookCopy.class, Rules.id(b.get("copyId")));
    var book = db.get(Book.class, c.bookId);
    access.library(book.departmentId, "circulation");
    Rules.check(c.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
    var p = db.get(Patron.class, Rules.id(b.get("patronId")));
    Rules.check(p.departmentId.equals(book.departmentId), "LIBRARY_MISMATCH");
    patronScope(p);
    ready(p);
    Rules.check(book.active, "BOOK_ARCHIVED");
    processBook(book);
    Rules.check(
        db.query(Loan.class, "from Loan where patronId=?1 and status='OPEN'", p.id).size()
            < admin.setting("max_loans"),
        "LOAN_LIMIT");
    Rules.check(
        db.query(Loan.class, "from Loan where copyId=?1 and status='OPEN'", c.id).isEmpty(),
        "COPY_BUSY");
    if (c.status.equals("HELD")) {
      var holds =
          db.query(BookHold.class, "from BookHold where copyId=?1 and status='READY'", c.id);
      Rules.check(holds.size() == 1 && holds.getFirst().patronId.equals(p.id), "HOLD_RESERVED");
      var h = holds.getFirst();
      h.status = "FULFILLED";
      h.closedAt = clock.instant();
      h.version++;
      event("HOLD_FULFILLED", book, c, p, null, h, "");
    } else Rules.check(c.status.equals("AVAILABLE"), "COPY_UNAVAILABLE");
    var l = new Loan();
    l.copyId = c.id;
    l.departmentId = book.departmentId;
    l.patronId = p.id;
    l.loanDate = today(book.departmentId);
    l.loanDays = admin.setting("loan_days");
    l.dueDate = l.loanDate.plusDays(l.loanDays);
    l.maxRenewals = admin.setting("max_renewals");
    l.createdAt = clock.instant();
    db.save(l);
    c.status = "ON_LOAN";
    c.version++;
    event("CHECKOUT", book, c, p, l, null, "");
    return l;
  }

  /** 正常归还、损坏归还或遗失结案；异常必须记录说明，不计算或收取罚金。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Loan checkin(Long id, Map<String, Object> b) {
    admin.lock();
    var l = db.get(Loan.class, id);
    access.library(l.departmentId, "circulation");
    patronScope(db.get(Patron.class, l.patronId));
    Rules.check(l.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
    Rules.check(l.status.equals("OPEN"), "LOAN_CLOSED");
    String condition = Rules.text(b.get("condition"), 20, true);
    Rules.check(Set.of("AVAILABLE", "DAMAGED", "LOST").contains(condition), "INVALID_INPUT");
    String note = Rules.paragraph(b.get("note"), 500, !condition.equals("AVAILABLE"));
    var c = db.get(BookCopy.class, l.copyId);
    Rules.check(c.status.equals("ON_LOAN"), "COPY_STATE_CONFLICT");
    c.status = condition;
    c.version++;
    l.status = condition.equals("LOST") ? "LOST" : "RETURNED";
    l.returnedAt = clock.instant();
    l.returnCondition = condition;
    l.note = note;
    l.version++;
    var book = db.get(Book.class, c.bookId);
    event(
        condition.equals("LOST") ? "LOST_CLOSED" : "CHECKIN",
        book,
        c,
        db.get(Patron.class, l.patronId),
        l,
        null,
        note);
    processBook(book);
    return l;
  }

  /** 读者续借自己的记录或馆员代办；有其他候补、逾期或达到快照次数时拒绝。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Loan renew(Long id, Map<String, Object> b) {
    admin.lock();
    var l = db.get(Loan.class, id);
    var p = db.get(Patron.class, l.patronId);
    patronScope(p);
    if (!access.has("circulation")) access.require("borrow");
    Rules.check(l.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
    Rules.check(l.status.equals("OPEN"), "LOAN_CLOSED");
    ready(p);
    Rules.check(l.renewals < l.maxRenewals, "RENEWAL_LIMIT");
    var c = db.get(BookCopy.class, l.copyId);
    var book = db.get(Book.class, c.bookId);
    Rules.check(book.active, "BOOK_ARCHIVED");
    processBook(book);
    Rules.check(
        db
            .query(
                BookHold.class,
                "from BookHold where bookId=?1 and status in ('WAITING','READY')",
                book.id)
            .stream()
            .noneMatch(h -> !h.patronId.equals(p.id)),
        "HOLD_WAITING");
    l.dueDate = l.dueDate.plusDays(l.loanDays);
    l.renewals++;
    l.version++;
    event("RENEW", book, c, p, l, null, "");
    return l;
  }

  /** 条码精确检索与当前借阅；馆员才可看到持有人，不自动执行借还。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object lookup(String barcode) {
    access.require("circulation");
    var rows =
        db.query(BookCopy.class, "from BookCopy where barcode=?1", LibraryPolicy.code(barcode));
    if (rows.isEmpty()) throw new Problem(404, "NOT_FOUND");
    var c = rows.getFirst();
    var b = db.get(Book.class, c.bookId);
    access.library(b.departmentId, "circulation");
    var out = new LinkedHashMap<String, Object>();
    out.put("copy", c);
    out.put("book", b);
    var loans = db.query(Loan.class, "from Loan where copyId=?1 and status='OPEN'", c.id);
    out.put("loan", loans.isEmpty() ? null : loanDto(loans.getFirst()));
    var holds = db.query(BookHold.class, "from BookHold where copyId=?1 and status='READY'", c.id);
    out.put("hold", holds.isEmpty() ? null : holdDto(holds.getFirst()));
    return out;
  }

  /** 自己的借阅证详情，没有证时返回空，不泄露其他人资料。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object me() {
    access.require("borrow");
    var rows = db.query(Patron.class, "from Patron where accountId=?1", access.current().id);
    return rows.isEmpty()
        ? Map.of("registered", false)
        : Map.of("registered", true, "patron", rows.getFirst());
  }

  /** 可见借阅DTO包括当前书名、借期及有效逾期判断。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object loanDto(Loan l) {
    var c = db.get(BookCopy.class, l.copyId);
    var p = db.get(Patron.class, l.patronId);
    return Map.of(
        "loan",
        l,
        "barcode",
        c.barcode,
        "book",
        db.get(Book.class, c.bookId).title,
        "patron",
        db.get(Account.class, p.accountId).displayName,
        "cardNo",
        p.cardNo,
        "overdue",
        l.status.equals("OPEN") && LibraryPolicy.overdue(l.dueDate, today(l.departmentId)));
  }

  /** 当前排队位置按相同书目未完成候补计算，不返回其他候补人员。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object holdDto(BookHold h) {
    var p = db.get(Patron.class, h.patronId);
    long position =
        h.status.equals("WAITING")
            ? db.query(
                        BookHold.class,
                        "from BookHold where bookId=?1 and status='WAITING' order by createdAt,id",
                        h.bookId)
                    .indexOf(h)
                + 1L
            : 0;
    return Map.of(
        "hold",
        h,
        "book",
        db.get(Book.class, h.bookId).title,
        "patron",
        db.get(Account.class, p.accountId).displayName,
        "cardNo",
        p.cardNo,
        "barcode",
        h.copyId == null ? "" : db.get(BookCopy.class, h.copyId).barcode,
        "position",
        position,
        "pickupExpired",
        h.expiresAt != null && !clock.instant().isBefore(h.expiresAt) && h.status.equals("READY"));
  }

  /** 馆员全部可见记录，读者严格自己的记录；完整分页、状态与关键词过滤。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object records(
      String kind, String query, String state, boolean mine, int page, int size, String sort) {
    boolean staff = access.has("circulation");
    if (!staff) access.require("borrow");
    Rules.integer(page, 1, 100000);
    Rules.integer(size, 1, 100);
    if (!Set.of("latest", "due").contains(sort) || !Set.of("loans", "holds").contains(kind))
      throw new Problem(400, "INVALID_INPUT");
    String q = Rules.text(query, 200, false).toLowerCase(Locale.ROOT);
    var rows = new ArrayList<Map<String, Object>>();
    if (kind.equals("loans")) {
      if (!Set.of("", "OPEN", "OVERDUE", "RETURNED", "LOST").contains(state))
        throw new Problem(400, "INVALID_INPUT");
      for (var l : db.all(Loan.class)) {
        var p = db.get(Patron.class, l.patronId);
        if (!access.patron(p) || ((mine || !staff) && !p.accountId.equals(access.current().id)))
          continue;
        boolean over =
            l.status.equals("OPEN") && LibraryPolicy.overdue(l.dueDate, today(l.departmentId));
        if (!state.isEmpty() && !(state.equals("OVERDUE") ? over : l.status.equals(state)))
          continue;
        var dto = (Map<String, Object>) loanDto(l);
        if (!(dto.get("book")
                + " "
                + dto.get("barcode")
                + " "
                + dto.get("patron")
                + " "
                + dto.get("cardNo"))
            .toLowerCase(Locale.ROOT)
            .contains(q)) continue;
        rows.add(dto);
      }
      rows.sort(
          sort.equals("due")
              ? Comparator.comparing(x -> ((Loan) x.get("loan")).dueDate)
              : Comparator.comparing((Map<String, Object> x) -> ((Loan) x.get("loan")).id)
                  .reversed());
    } else {
      if (!Set.of("", "WAITING", "READY", "FULFILLED", "CANCELLED", "EXPIRED").contains(state))
        throw new Problem(400, "INVALID_INPUT");
      for (var h : db.all(BookHold.class)) {
        var p = db.get(Patron.class, h.patronId);
        if (!access.patron(p)
            || ((mine || !staff) && !p.accountId.equals(access.current().id))
            || (!state.isEmpty() && !state.equals(h.status))) continue;
        var dto = (Map<String, Object>) holdDto(h);
        if (!(dto.get("book") + " " + dto.get("patron") + " " + dto.get("cardNo"))
            .toLowerCase(Locale.ROOT)
            .contains(q)) continue;
        rows.add(dto);
      }
      rows.sort(
          Comparator.comparing((Map<String, Object> x) -> ((BookHold) x.get("hold")).id)
              .reversed());
    }
    return Map.of(
        "items",
        rows.stream().skip((long) (page - 1) * size).limit(size).toList(),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 授权统计和CSV仅包含当前馆范围，不把匿名访问量当业务。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object report() {
    access.require("reports");
    var books = db.all(Book.class).stream().filter(b -> access.department(b.departmentId)).toList();
    var copies =
        db.all(BookCopy.class).stream()
            .filter(c -> access.department(db.get(Book.class, c.bookId).departmentId))
            .toList();
    var loans =
        db.all(Loan.class).stream()
            .filter(
                l ->
                    access.department(l.departmentId)
                        && (!access.role().scope.equals("ASSIGNED")
                            || db.get(Patron.class, l.patronId)
                                .accountId
                                .equals(access.current().id)))
            .toList();
    var holds =
        db.all(BookHold.class).stream()
            .filter(
                h ->
                    access.department(h.departmentId)
                        && (!access.role().scope.equals("ASSIGNED")
                            || db.get(Patron.class, h.patronId)
                                .accountId
                                .equals(access.current().id)))
            .toList();
    var metrics = new LinkedHashMap<String, Object>();
    metrics.put("books", books.size());
    metrics.put("copies", copies.size());
    metrics.put("available", copies.stream().filter(c -> c.status.equals("AVAILABLE")).count());
    metrics.put("onLoan", copies.stream().filter(c -> c.status.equals("ON_LOAN")).count());
    metrics.put("held", copies.stream().filter(c -> c.status.equals("HELD")).count());
    metrics.put(
        "overdue",
        loans.stream()
            .filter(
                l ->
                    l.status.equals("OPEN")
                        && LibraryPolicy.overdue(l.dueDate, today(l.departmentId)))
            .count());
    metrics.put("waiting", holds.stream().filter(h -> h.status.equals("WAITING")).count());
    metrics.put("damaged", copies.stream().filter(c -> c.status.equals("DAMAGED")).count());
    metrics.put("lost", copies.stream().filter(c -> c.status.equals("LOST")).count());
    metrics.put(
        "patrons",
        db.all(Patron.class).stream()
            .filter(
                p ->
                    access.department(p.departmentId)
                        && (!access.role().scope.equals("ASSIGNED")
                            || p.accountId.equals(access.current().id)))
            .count());
    return Map.of(
        "metrics",
        metrics,
        "rows",
        loans.stream()
            .sorted(Comparator.comparing((Loan l) -> l.dueDate))
            .map(this::loanDto)
            .toList());
  }

  /** 精确可见借阅台账导出；公式前缀防护和UTF-8，不包含口令。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String csv() {
    var report = (Map<String, Object>) report();
    var rows = (List<Map<String, Object>>) report.get("rows");
    var out =
        new StringBuilder(
            "loan_id,book,barcode,patron,card_no,loan_date,due_date,status,renewals\r\n");
    for (var x : rows) {
      var l = (Loan) x.get("loan");
      var values =
          List.of(
              l.id,
              x.get("book"),
              x.get("barcode"),
              x.get("patron"),
              x.get("cardNo"),
              l.loanDate,
              l.dueDate,
              l.status,
              l.renewals);
      out.append(values.stream().map(Rules::csv).collect(java.util.stream.Collectors.joining(",")))
          .append("\r\n");
    }
    return out.toString();
  }
}
