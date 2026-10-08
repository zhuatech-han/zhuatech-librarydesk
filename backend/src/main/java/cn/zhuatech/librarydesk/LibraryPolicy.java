// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import java.time.*;

/** 自然日到期与ISBN校验；不推断罚金或外部目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class LibraryPolicy {
  /** 到期日当天仍有效，次日才逾期；以图书馆IANA时区计算。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean overdue(LocalDate due, LocalDate today) {
    return today.isAfter(due);
  }

  /** 严格可选ISBN-10/13检查位，仅去除用户常见空格/连字符。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String isbn(Object input) {
    String s = Rules.text(input, 30, false).replace("-", "").replace(" ", "").toUpperCase();
    if (s.isEmpty()) return s;
    int sum = 0;
    if (s.matches("[0-9]{9}[0-9X]")) {
      for (int i = 0; i < 10; i++) sum += (10 - i) * (s.charAt(i) == 'X' ? 10 : s.charAt(i) - '0');
      if (sum % 11 == 0) return s;
    }
    if (s.matches("[0-9]{13}")) {
      for (int i = 0; i < 13; i++) sum += (i % 2 == 0 ? 1 : 3) * (s.charAt(i) - '0');
      if (sum % 10 == 0) return s;
    }
    throw new Problem(400, "ISBN_INVALID");
  }

  /** 稳定键统一大写，禁止路径与控制字符。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String code(Object value) {
    String s = Rules.text(value, 40, true).toUpperCase(java.util.Locale.ROOT);
    if (!s.matches("[A-Z0-9][A-Z0-9_.-]{2,39}")) throw new Problem(400, "CODE_INVALID");
    return s;
  }

  private LibraryPolicy() {}
}
