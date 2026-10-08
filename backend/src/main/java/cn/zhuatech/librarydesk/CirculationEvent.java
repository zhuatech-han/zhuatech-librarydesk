// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import jakarta.persistence.*;
import java.time.*;

/** 不可编辑的流通事件；历史不注入广告。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "circulation_event")
public class CirculationEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "book_id", nullable = true)
  public Long bookId;

  @Column(name = "copy_id", nullable = true)
  public Long copyId;

  @Column(name = "patron_id", nullable = true)
  public Long patronId;

  @Column(name = "loan_id", nullable = true)
  public Long loanId;

  @Column(name = "hold_id", nullable = true)
  public Long holdId;

  @Column(name = "action", nullable = false, length = 40)
  public String action;

  @Column(name = "note", nullable = false, length = 1000)
  public String note = "";

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
