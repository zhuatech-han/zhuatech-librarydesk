// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

/** ISBN、自然日边界、条码与CSV反例验证。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class LibraryPolicyTest {
  @Test
  void isbn13() {
    assertEquals("9780306406157", LibraryPolicy.isbn("978-0-306-40615-7"));
  }

  @Test
  void isbn10() {
    assertEquals("0306406152", LibraryPolicy.isbn("0306406152"));
  }

  @Test
  void isbnX() {
    assertEquals("080442957X", LibraryPolicy.isbn("080442957x"));
  }

  @Test
  void invalidChecksum() {
    assertThrows(Problem.class, () -> LibraryPolicy.isbn("9780306406158"));
  }

  @Test
  void invalidIsbnLength() {
    assertThrows(Problem.class, () -> LibraryPolicy.isbn("123"));
  }

  @Test
  void optionalIsbn() {
    assertEquals("", LibraryPolicy.isbn(null));
  }

  @Test
  void exactDueDay() {
    assertFalse(LibraryPolicy.overdue(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8)));
  }

  @Test
  void followingDay() {
    assertTrue(LibraryPolicy.overdue(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9)));
  }

  @Test
  void barcodeUppercase() {
    assertEquals("BK-001", LibraryPolicy.code("bk-001"));
  }

  @Test
  void barcodePathRejected() {
    assertThrows(Problem.class, () -> LibraryPolicy.code("../BOOK"));
  }

  @Test
  void csvFormula() {
    assertEquals("\"'=1+2\"", Rules.csv("=1+2"));
  }

  @Test
  void csvHiddenPrefix() {
    assertTrue(Rules.csv("﻿ =SUM(A1)").contains("'"));
  }

  @Test
  void integerNotFloat() {
    assertThrows(Problem.class, () -> Rules.integer("1.2", 0, 100));
  }

  @Test
  void multilineNoteRetainsContent() {
    assertEquals("line one\nline two", Rules.paragraph("line one\r\nline two", 500, true));
  }

  @Test
  void hiddenNoteControlRejected() {
    assertThrows(Problem.class, () -> Rules.paragraph("line\u0000two", 500, false));
  }
}
