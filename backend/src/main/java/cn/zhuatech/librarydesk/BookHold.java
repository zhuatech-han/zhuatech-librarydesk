// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import jakarta.persistence.*;
import java.time.*;

/** 书目级FIFO预约；候补和具体待领单册清楚分离。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "book_hold")
public class BookHold {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "book_id", nullable = false)
  public Long bookId;

  @Column(name = "patron_id", nullable = false)
  public Long patronId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "copy_id", nullable = true)
  public Long copyId;

  @Column(name = "status", nullable = false, length = 20)
  public String status = "WAITING";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "ready_at", nullable = true)
  public Instant readyAt;

  @Column(name = "expires_at", nullable = true)
  public Instant expiresAt;

  @Column(name = "closed_at", nullable = true)
  public Instant closedAt;

  @Column(name = "reason", nullable = false, length = 1000)
  public String reason = "";

  @Column(nullable = false)
  public long version = 1;
}
