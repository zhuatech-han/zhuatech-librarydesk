// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import jakarta.persistence.*;
import java.time.*;

/** 书目元数据；书目与实际单册分离，归档保留历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "book_title")
public class Book {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "title", nullable = false, length = 200)
  public String title;

  @Column(name = "author", nullable = false, length = 160)
  public String author;

  @Column(name = "isbn", nullable = false, length = 20)
  public String isbn = "";

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "language", nullable = false, length = 40)
  public String language = "zh-CN";

  @Column(name = "description", nullable = false, length = 2000)
  public String description = "";

  @Column(name = "active", nullable = false)
  public boolean active = true;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public long version = 1;
}
