// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import jakarta.persistence.*;
import java.time.*;

/** 独立单册条码与流通状态；条码和书目身份不可变。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "book_copy")
public class BookCopy {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "book_id", nullable = false)
  public Long bookId;

  @Column(name = "barcode", nullable = false, length = 40)
  public String barcode;

  @Column(name = "shelf", nullable = false, length = 80)
  public String shelf = "";

  @Column(name = "status", nullable = false, length = 20)
  public String status = "AVAILABLE";

  @Column(name = "note", nullable = false, length = 500)
  public String note = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public long version = 1;
}
