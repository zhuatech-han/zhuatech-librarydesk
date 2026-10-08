// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import java.math.*;
import java.util.*;

/** 输入、导入和导出安全规则；不静默舍入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class Rules {
  /** 明确业务错误。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void check(boolean ok, String code) {
    if (!ok) throw new Problem(409, code);
  }

  /** 严格文本，拒绝控制字符和空白必填。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String text(Object value, int max, boolean required) {
    String s = Objects.toString(value, "").trim();
    if (s.length() > max
        || s.chars().anyMatch(c -> Character.isISOControl(c))
        || (required && s.isEmpty())) throw new Problem(400, "INVALID_INPUT");
    return s;
  }

  /** 多行描述和备注规范换行，仍拒绝隐藏控制字符。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String paragraph(Object value, int max, boolean required) {
    String s = Objects.toString(value, "").replace("\r\n", "\n").replace('\r', '\n').trim();
    if (s.length() > max
        || s.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')
        || (required && s.isEmpty())) throw new Problem(400, "INVALID_INPUT");
    return s;
  }

  /** 整数拒绝浮点、负数和超限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static int integer(Object value, int min, int max) {
    try {
      int n = new BigDecimal(Objects.toString(value, "")).intValueExact();
      if (n < min || n > max) throw new IllegalArgumentException();
      return n;
    } catch (Exception e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }

  /** 标识符须为正整数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Long id(Object value) {
    try {
      long n = new BigDecimal(Objects.toString(value, "")).longValueExact();
      if (n < 1) throw new IllegalArgumentException();
      return n;
    } catch (Exception e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }

  /** 明确真假；不把任意字符串当真。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean flag(Object value) {
    if (!(value instanceof Boolean b)) throw new Problem(400, "INVALID_INPUT");
    return b;
  }

  /** CSV保留引用与换行转义，阻止公式前缀。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String csv(Object value) {
    String s = Objects.toString(value, "");
    int k = 0;
    while (k < s.length()
        && (Character.isWhitespace(s.charAt(k))
            || Character.isISOControl(s.charAt(k))
            || s.charAt(k) == '\uFEFF'
            || s.charAt(k) == '\u200B')) k++;
    if (k < s.length() && "=+-@".indexOf(s.charAt(k)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  private Rules() {}
}
