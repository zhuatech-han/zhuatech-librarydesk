// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 参数、权限、并发与业务错误的统一安全响应。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech
 * / zhuatech2
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorHandler {
  /**
   * 返回明确业务错误。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  @ExceptionHandler(Problem.class)
  ResponseEntity<?> business(Problem e) {
    var body = new java.util.LinkedHashMap<String, Object>();
    body.put("code", e.getMessage());

    return ResponseEntity.status(e.status).body(body);
  }

  /**
   * 隐藏约束内部细节。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  @ExceptionHandler({
    DataIntegrityViolationException.class,
    jakarta.persistence.PersistenceException.class
  })
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409).body(Map.of("code", "CONFLICT"));
  }

  /** 请求载荷超限返回413，不返回服务器路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> tooLarge(Exception e) {
    return ResponseEntity.status(413).body(Map.of("code", "FILE_TOO_LARGE"));
  }

  /**
   * 校验和类型转换错误返回 400。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  @ExceptionHandler({
    IllegalArgumentException.class,
    ClassCastException.class,
    java.time.DateTimeException.class,
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("code", "INVALID_INPUT"));
  }
}
