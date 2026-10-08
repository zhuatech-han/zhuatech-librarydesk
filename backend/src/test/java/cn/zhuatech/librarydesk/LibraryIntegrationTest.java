// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 真实HTTP、JPA、Flyway和确定时钟验证借还/候补/范围，不模拟业务服务。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import(LibraryIntegrationTest.TimeConfig.class)
class LibraryIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:library;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("librarydesk.admin-password", () -> PASSWORD);
  }

  /** 可移动的测试时钟，实际HTTP服务仍使用相同注入Clock。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class TestClock extends Clock {
    volatile Instant now = Instant.parse("2026-10-08T08:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return Clock.fixed(now, z);
    }

    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired TestClock clock;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, staff, reader, second, other;
  String suffix;
  long bookId, copyId, pid, secondPid, readerId, staffId, otherDept;
  JsonNode book;

  Map<String, Object> m(Object... a) {
    var v = new LinkedHashMap<String, Object>();
    for (int i = 0; i < a.length; i += 2) v.put(a[i].toString(), a[i + 1]);
    return v;
  }

  MockHttpSession login(String name) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(m("username", name, "password", PASSWORD))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  MockHttpServletRequestBuilder req(String path, String method, Map<String, Object> b)
      throws Exception {
    var r =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (b != null)
      r.with(csrf()).contentType("application/json").content(json.writeValueAsString(b));
    return r;
  }

  MvcResult result(MockHttpSession session, String path, String method, Map<String, Object> b)
      throws Exception {
    return mvc.perform(req(path, method, b).session(session)).andReturn();
  }

  JsonNode call(MockHttpSession session, String path, String method, Map<String, Object> b)
      throws Exception {
    var r = result(session, path, method, b);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(
      MockHttpSession session,
      String path,
      String method,
      Map<String, Object> b,
      int status,
      String code)
      throws Exception {
    var r = result(session, path, method, b);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).get("code").asString());
  }

  long user(String name, long role, long dept) throws Exception {
    return call(
            admin,
            "/admin/users",
            "POST",
            m(
                "username",
                name,
                "displayName",
                "TEST " + name,
                "roleId",
                role,
                "departmentId",
                dept,
                "enabled",
                true,
                "password",
                PASSWORD))
        .get("id")
        .asLong();
  }

  Map<String, Object> map(JsonNode n) {
    return json.convertValue(n, Map.class);
  }

  void setting(String code, String value) throws Exception {
    for (var r : call(admin, "/admin/settings", "GET", null)) {
      if (r.get("code").asString().equals(code)) {
        var b = map(r);
        b.put("value", value);
        call(admin, "/admin/settings/" + r.get("id").asLong(), "PUT", b);
        return;
      }
    }
    throw new AssertionError();
  }

  JsonNode newCopy(String code) throws Exception {
    return call(
        staff,
        "/books/" + bookId + "/copies",
        "POST",
        m("barcode", code, "shelf", "TEST A-01", "note", ""));
  }

  JsonNode copy() throws Exception {
    for (var c : call(staff, "/books/" + bookId, "GET", null).get("copies"))
      if (c.get("id").asLong() == copyId) return c;
    throw new AssertionError();
  }

  JsonNode checkout(long patron) throws Exception {
    return call(
        staff,
        "/checkout",
        "POST",
        m("copyId", copyId, "patronId", patron, "version", copy().get("version").asLong()));
  }

  JsonNode reserve(MockHttpSession s) throws Exception {
    return call(s, "/holds", "POST", m("bookId", bookId));
  }

  JsonNode cancel(MockHttpSession s, JsonNode h) throws Exception {
    return call(
        s,
        "/holds/" + h.get("id").asLong() + "/cancel",
        "POST",
        m("version", h.get("version").asLong(), "reason", "TEST cancelled"));
  }

  JsonNode returned(JsonNode l, String condition) throws Exception {
    return call(
        staff,
        "/loans/" + l.get("id").asLong() + "/return",
        "POST",
        m(
            "version",
            l.get("version").asLong(),
            "condition",
            condition,
            "note",
            condition.equals("AVAILABLE") ? "" : "TEST condition"));
  }

  @BeforeEach
  void setup() throws Exception {
    clock.now = Instant.parse("2026-10-08T08:00:00Z");
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    setting("loan_days", "14");
    setting("max_loans", "5");
    setting("max_renewals", "2");
    setting("hold_hours", "48");
    setting("max_holds", "3");
    staffId = user("staff" + suffix, 2, 1);
    readerId = user("reader" + suffix, 3, 1);
    long secondId = user("second" + suffix, 3, 1);
    staff = login("staff" + suffix);
    reader = login("reader" + suffix);
    second = login("second" + suffix);
    otherDept =
        call(
                admin,
                "/admin/departments",
                "POST",
                m("name", "TEST other " + suffix, "zone", "UTC", "enabled", true))
            .get("id")
            .asLong();
    user("other" + suffix, 2, otherDept);
    other = login("other" + suffix);
    pid =
        call(
                staff,
                "/patrons",
                "POST",
                m("accountId", readerId, "cardNo", "P-" + suffix, "active", true, "note", ""))
            .get("id")
            .asLong();
    secondPid =
        call(
                staff,
                "/patrons",
                "POST",
                m("accountId", secondId, "cardNo", "Q-" + suffix, "active", true, "note", ""))
            .get("id")
            .asLong();
    book =
        call(
            staff,
            "/books",
            "POST",
            m(
                "departmentId",
                1,
                "title",
                "TEST 书目 " + suffix,
                "author",
                "TEST author",
                "isbn",
                "",
                "category",
                "SCIENCE",
                "language",
                "zh-CN",
                "description",
                "TEST catalog",
                "active",
                true));
    bookId = book.get("id").asLong();
    copyId = newCopy("B-" + suffix).get("id").asLong();
  }

  @Test
  void normalCirculationPersistsHistory() throws Exception {
    var l = checkout(pid);
    assertEquals("OPEN", l.get("status").asString());
    assertEquals("2026-10-22", l.get("dueDate").asString());
    assertEquals("ON_LOAN", copy().get("status").asString());
    var closed = returned(l, "AVAILABLE");
    assertEquals("RETURNED", closed.get("status").asString());
    assertEquals("AVAILABLE", copy().get("status").asString());
    assertEquals(1, call(reader, "/records/loans", "GET", null).get("total").asInt());
    assertTrue(call(staff, "/books/" + bookId, "GET", null).get("events").size() >= 2);
  }

  @Test
  void readerCannotBorrowAtCounter() throws Exception {
    fail(
        reader,
        "/checkout",
        "POST",
        m("copyId", copyId, "patronId", pid, "version", 1),
        403,
        "FORBIDDEN");
    fail(reader, "/patrons", "GET", null, 403, "FORBIDDEN");
    fail(reader, "/admin/users", "GET", null, 403, "FORBIDDEN");
  }

  @Test
  void crossLibraryScope() throws Exception {
    fail(other, "/books/" + bookId, "GET", null, 403, "OUT_OF_SCOPE");
    fail(other, "/lookup?barcode=B-" + suffix, "GET", null, 403, "OUT_OF_SCOPE");
    assertEquals(0, call(other, "/books", "GET", null).get("total").asInt());
    assertEquals(0, call(other, "/records/loans", "GET", null).get("total").asInt());
  }

  @Test
  void myLoansDoNotLeakOtherPatrons() throws Exception {
    checkout(pid);
    assertEquals(1, call(reader, "/records/loans?mine=false", "GET", null).get("total").asInt());
    assertEquals(0, call(second, "/records/loans?mine=false", "GET", null).get("total").asInt());
    assertFalse(call(second, "/books/" + bookId, "GET", null).has("events"));
  }

  @Test
  void readerCannotForgeHoldPatron() throws Exception {
    fail(reader, "/holds", "POST", m("bookId", bookId, "patronId", secondPid), 403, "OUT_OF_SCOPE");
    assertEquals(0, call(reader, "/records/holds", "GET", null).get("total").asInt());
  }

  @Test
  void holdReservesCopyForRightReader() throws Exception {
    var h = reserve(reader);
    assertEquals("READY", h.get("status").asString());
    fail(
        staff,
        "/checkout",
        "POST",
        m("copyId", copyId, "patronId", secondPid, "version", copy().get("version").asLong()),
        409,
        "HOLD_RESERVED");
    checkout(pid);
    assertEquals(
        "FULFILLED",
        call(reader, "/records/holds", "GET", null)
            .get("items")
            .get(0)
            .get("hold")
            .get("status")
            .asString());
  }

  @Test
  void holdQueueServesFifo() throws Exception {
    var loan = checkout(pid);
    var first = reserve(second);
    long thirdId = user("third" + suffix, 3, 1);
    var third = login("third" + suffix);
    call(
        staff,
        "/patrons",
        "POST",
        m("accountId", thirdId, "cardNo", "R-" + suffix, "active", true, "note", ""));
    var next = reserve(third);
    assertEquals("WAITING", first.get("status").asString());
    returned(loan, "AVAILABLE");
    assertEquals("HELD", copy().get("status").asString());
    assertEquals(
        "READY",
        call(second, "/records/holds", "GET", null)
            .get("items")
            .get(0)
            .get("hold")
            .get("status")
            .asString());
    assertEquals(
        "WAITING",
        call(third, "/records/holds", "GET", null)
            .get("items")
            .get(0)
            .get("hold")
            .get("status")
            .asString());
  }

  @Test
  void cancelReleasesForNextQueue() throws Exception {
    var first = reserve(reader);
    reserve(second);
    cancel(reader, first);
    assertEquals(
        "READY",
        call(second, "/records/holds", "GET", null)
            .get("items")
            .get(0)
            .get("hold")
            .get("status")
            .asString());
  }

  @Test
  void pickupExpiresExactlyAtBoundary() throws Exception {
    reserve(reader);
    reserve(second);
    clock.now = clock.now.plusSeconds(48 * 3600L);
    call(staff, "/holds/process", "POST", m());
    assertEquals(
        "EXPIRED",
        call(reader, "/records/holds", "GET", null)
            .get("items")
            .get(0)
            .get("hold")
            .get("status")
            .asString());
    assertEquals(
        "READY",
        call(second, "/records/holds", "GET", null)
            .get("items")
            .get(0)
            .get("hold")
            .get("status")
            .asString());
  }

  @Test
  void renewalBlockedByPendingHold() throws Exception {
    var l = checkout(pid);
    reserve(second);
    fail(
        reader,
        "/loans/" + l.get("id").asLong() + "/renew",
        "POST",
        m("version", 1),
        409,
        "HOLD_WAITING");
  }

  @Test
  void renewalUsesOriginalSnapshot() throws Exception {
    var l = checkout(pid);
    setting("loan_days", "30");
    setting("max_renewals", "0");
    var v = call(reader, "/loans/" + l.get("id").asLong() + "/renew", "POST", m("version", 1));
    assertEquals("2026-11-05", v.get("dueDate").asString());
    assertEquals(14, v.get("loanDays").asInt());
    v = call(reader, "/loans/" + l.get("id").asLong() + "/renew", "POST", m("version", 2));
    fail(
        reader,
        "/loans/" + l.get("id").asLong() + "/renew",
        "POST",
        m("version", 3),
        409,
        "RENEWAL_LIMIT");
  }

  @Test
  void dueDayValidNextDayOverdue() throws Exception {
    var l = checkout(pid);
    clock.now = Instant.parse("2026-10-22T15:59:59Z");
    assertFalse(
        call(reader, "/records/loans", "GET", null).get("items").get(0).get("overdue").asBoolean());
    clock.now = Instant.parse("2026-10-22T16:00:00Z");
    assertTrue(
        call(reader, "/records/loans", "GET", null).get("items").get(0).get("overdue").asBoolean());
    fail(
        reader,
        "/loans/" + l.get("id").asLong() + "/renew",
        "POST",
        m("version", 1),
        409,
        "PATRON_OVERDUE");
  }

  @Test
  void overdueCannotBorrowOrPlaceHold() throws Exception {
    checkout(pid);
    clock.now = clock.now.plusSeconds(15 * 86400L);
    var c = newCopy("C-" + suffix);
    fail(
        staff,
        "/checkout",
        "POST",
        m("copyId", c.get("id").asLong(), "patronId", pid, "version", 1),
        409,
        "PATRON_OVERDUE");
    fail(reader, "/holds", "POST", m("bookId", bookId), 409, "PATRON_OVERDUE");
  }

  @Test
  void damagedReturnDoesNotAllocate() throws Exception {
    var l = checkout(pid);
    reserve(second);
    returned(l, "DAMAGED");
    assertEquals("DAMAGED", copy().get("status").asString());
    assertEquals(
        "WAITING",
        call(second, "/records/holds", "GET", null)
            .get("items")
            .get(0)
            .get("hold")
            .get("status")
            .asString());
  }

  @Test
  void repairedCopyReturnsToHoldQueue() throws Exception {
    var l = checkout(pid);
    reserve(second);
    returned(l, "DAMAGED");
    call(
        staff,
        "/copies/" + copyId,
        "PUT",
        m(
            "version",
            copy().get("version").asLong(),
            "status",
            "AVAILABLE",
            "shelf",
            "A",
            "note",
            "TEST repaired"));
    call(staff, "/holds/process", "POST", m());
    assertEquals("HELD", copy().get("status").asString());
  }

  @Test
  void lossNeedsReasonAndClosesLoan() throws Exception {
    var l = checkout(pid);
    fail(
        staff,
        "/loans/" + l.get("id").asLong() + "/return",
        "POST",
        m("version", 1, "condition", "LOST", "note", ""),
        400,
        "INVALID_INPUT");
    var lost = returned(l, "LOST");
    assertEquals("LOST", lost.get("status").asString());
    assertEquals("LOST", copy().get("status").asString());
  }

  @Test
  void busyCopyCannotBeRewritten() throws Exception {
    checkout(pid);
    fail(
        staff,
        "/copies/" + copyId,
        "PUT",
        m(
            "version",
            copy().get("version").asLong(),
            "status",
            "AVAILABLE",
            "shelf",
            "",
            "note",
            "TEST"),
        409,
        "COPY_BUSY");
  }

  @Test
  void duplicateBarcodeAndReaderCardRejected() throws Exception {
    fail(
        staff,
        "/books/" + bookId + "/copies",
        "POST",
        m("barcode", "b-" + suffix, "shelf", "", "note", ""),
        409,
        "BARCODE_DUPLICATE");
    fail(
        staff,
        "/patrons",
        "POST",
        m("accountId", readerId, "cardNo", "XX-" + suffix, "active", true, "note", ""),
        409,
        "PATRON_DUPLICATE");
  }

  @Test
  void metadataInputValidation() throws Exception {
    var b = map(book);
    b.put("isbn", "1234567890123");
    fail(staff, "/books/" + bookId, "PUT", b, 400, "ISBN_INVALID");
    b.put("isbn", "");
    b.put("language", "invalid language");
    fail(staff, "/books/" + bookId, "PUT", b, 409, "LANGUAGE_INVALID");
  }

  @Test
  void noDoubleCheckoutOrDoubleReturn() throws Exception {
    var l = checkout(pid);
    fail(
        staff,
        "/checkout",
        "POST",
        m("copyId", copyId, "patronId", secondPid, "version", copy().get("version").asLong()),
        409,
        "COPY_BUSY");
    returned(l, "AVAILABLE");
    fail(
        staff,
        "/loans/" + l.get("id").asLong() + "/return",
        "POST",
        m("version", 1, "condition", "AVAILABLE", "note", ""),
        409,
        "VERSION_CONFLICT");
  }

  @Test
  void readerCannotCancelOthersHold() throws Exception {
    var h = reserve(reader);
    fail(
        second,
        "/holds/" + h.get("id").asLong() + "/cancel",
        "POST",
        m("version", h.get("version").asLong(), "reason", "TEST"),
        403,
        "OUT_OF_SCOPE");
  }

  @Test
  void disabledPatronStillCanReturn() throws Exception {
    var l = checkout(pid);
    JsonNode p = null;
    for (var row : call(staff, "/patrons", "GET", null))
      if (row.get("patron").get("id").asLong() == pid) p = row.get("patron");
    var b = map(p);
    b.put("active", false);
    call(staff, "/patrons/" + pid, "PUT", b);
    fail(
        reader,
        "/loans/" + l.get("id").asLong() + "/renew",
        "POST",
        m("version", 1),
        409,
        "PATRON_DISABLED");
    assertEquals("RETURNED", returned(l, "AVAILABLE").get("status").asString());
  }

  @Test
  void disabledHolderReleasesCopy() throws Exception {
    reserve(reader);
    reserve(second);
    JsonNode p = null;
    for (var row : call(staff, "/patrons", "GET", null))
      if (row.get("patron").get("id").asLong() == pid) p = row.get("patron");
    var b = map(p);
    b.put("active", false);
    call(staff, "/patrons/" + pid, "PUT", b);
    call(staff, "/holds/process", "POST", m());
    assertEquals(
        "CANCELLED",
        call(reader, "/records/holds", "GET", null)
            .get("items")
            .get(0)
            .get("hold")
            .get("status")
            .asString());
  }

  @Test
  void archiveAllowsOldLoanReturn() throws Exception {
    var l = checkout(pid);
    var b = map(book);
    b.put("active", false);
    call(staff, "/books/" + bookId, "PUT", b);
    fail(reader, "/books/" + bookId, "GET", null, 404, "NOT_FOUND");
    assertEquals("RETURNED", returned(l, "AVAILABLE").get("status").asString());
  }

  @Test
  void archiveBlockedWhenHoldsActive() throws Exception {
    reserve(reader);
    var b = map(book);
    b.put("active", false);
    fail(staff, "/books/" + bookId, "PUT", b, 409, "ACTIVE_HOLDS");
  }

  @Test
  void loanLimitsAreEnforced() throws Exception {
    setting("max_loans", "1");
    checkout(pid);
    var c = newCopy("C-" + suffix);
    fail(
        staff,
        "/checkout",
        "POST",
        m("copyId", c.get("id").asLong(), "patronId", pid, "version", 1),
        409,
        "LOAN_LIMIT");
  }

  @Test
  void searchPaginationAndNoInvalidSort() throws Exception {
    var r = call(reader, "/books?q=" + suffix + "&size=1&page=1&sort=title", "GET", null);
    assertEquals(1, r.get("total").asInt());
    assertEquals(1, r.get("items").size());
    fail(reader, "/books?sort=evil", "GET", null, 400, "INVALID_INPUT");
    fail(reader, "/records/loans?size=0", "GET", null, 400, "INVALID_INPUT");
  }

  @Test
  void unregisteredAccountCannotReserve() throws Exception {
    user("unreg" + suffix, 3, 1);
    var s = login("unreg" + suffix);
    assertFalse(call(s, "/my", "GET", null).get("registered").asBoolean());
    fail(s, "/holds", "POST", m("bookId", bookId), 409, "PATRON_NOT_REGISTERED");
  }

  @Test
  void accountDepartmentIdentityProtected() throws Exception {
    JsonNode a = null;
    for (var row : call(admin, "/admin/users", "GET", null))
      if (row.get("id").asLong() == readerId) a = row;
    var b = map(a);
    b.put("departmentId", otherDept);
    b.put("password", "");
    fail(admin, "/admin/users/" + readerId, "PUT", b, 409, "ACCOUNT_ASSIGNED");
  }

  @Test
  void lastAdminAndScopeAreProtected() throws Exception {
    JsonNode a = null;
    for (var row : call(admin, "/admin/users", "GET", null))
      if (row.get("username").asString().equals("admin")) a = row;
    var b = map(a);
    b.put("enabled", false);
    b.put("password", "");
    fail(admin, "/admin/users/" + a.get("id").asLong(), "PUT", b, 409, "LAST_ADMIN");
    fail(staff, "/admin/settings", "GET", null, 403, "FORBIDDEN");
  }

  @Test
  void csrfAndAnonymousGuard() throws Exception {
    assertEquals(401, mvc.perform(get("/api/books")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/holds").session(reader).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void accountDisableRevokesSession() throws Exception {
    JsonNode a = null;
    for (var row : call(admin, "/admin/users", "GET", null))
      if (row.get("id").asLong() == readerId) a = row;
    var b = map(a);
    b.put("enabled", false);
    b.put("password", "");
    call(admin, "/admin/users/" + readerId, "PUT", b);
    fail(reader, "/auth/me", "GET", null, 401, "UNAUTHENTICATED");
  }

  @Test
  void staleBookVersionAndHistoryDeletion() throws Exception {
    var b = map(book);
    b.put("title", "TEST renamed");
    call(staff, "/books/" + bookId, "PUT", b);
    fail(staff, "/books/" + bookId, "PUT", b, 409, "VERSION_CONFLICT");
    checkout(pid);
    fail(
        staff,
        "/copies/" + copyId + "?version=" + copy().get("version").asLong(),
        "DELETE",
        m(),
        409,
        "RECORD_REFERENCED");
  }

  @Test
  void concurrentCheckoutHasSingleWinner() throws Exception {
    var v = copy().get("version").asLong();
    var pool = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                gate.await();
                return result(
                        staff,
                        "/checkout",
                        "POST",
                        m("copyId", copyId, "patronId", pid, "version", v))
                    .getResponse()
                    .getStatus();
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return result(
                        staff,
                        "/checkout",
                        "POST",
                        m("copyId", copyId, "patronId", secondPid, "version", v))
                    .getResponse()
                    .getStatus();
              });
      gate.countDown();
      var statuses = new ArrayList<>(List.of(a.get(), b.get()));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void exportChecksScopeAndNoPasswords() throws Exception {
    checkout(pid);
    var text = result(staff, "/reports/export", "GET", null).getResponse().getContentAsString();
    assertTrue(text.contains(("B-" + suffix).toUpperCase(Locale.ROOT)));
    assertFalse(text.contains(PASSWORD));
    assertFalse(
        result(other, "/reports/export", "GET", null)
            .getResponse()
            .getContentAsString()
            .contains(("B-" + suffix).toUpperCase(Locale.ROOT)));
    fail(reader, "/reports/export", "GET", null, 403, "FORBIDDEN");
    assertFalse(
        result(admin, "/admin/users", "GET", null)
            .getResponse()
            .getContentAsString()
            .contains("passwordHash"));
  }

  @Test
  void disabledAccountCardCanBeDisabledWithNote() throws Exception {
    JsonNode a = null;
    for (var row : call(admin, "/admin/users", "GET", null))
      if (row.get("id").asLong() == readerId) a = row;
    var account = map(a);
    account.put("enabled", false);
    account.put("password", "");
    call(admin, "/admin/users/" + readerId, "PUT", account);
    JsonNode p = null;
    for (var row : call(staff, "/patrons", "GET", null))
      if (row.get("patron").get("id").asLong() == pid) p = row.get("patron");
    var card = map(p);
    card.put("active", false);
    card.put("note", "TEST 停用\n保留历史");
    var saved = call(staff, "/patrons/" + pid, "PUT", card);
    assertFalse(saved.get("active").asBoolean());
    assertEquals("TEST 停用\n保留历史", saved.get("note").asString());
  }

  @Test
  void initialMenusMatchRolePermissions() throws Exception {
    assertTrue(call(admin, "/auth/me", "GET", null).get("menus").size() >= 9);
    var menus = call(reader, "/auth/me", "GET", null).get("menus");
    assertEquals(2, menus.size());
    assertTrue(menus.toString().contains("catalog"));
    assertTrue(menus.toString().contains("my"));
  }
}
