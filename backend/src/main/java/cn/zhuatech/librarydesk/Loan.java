// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import jakarta.persistence.*;
import java.time.*;

/** 借阅单保留借期策略快照和真实归还结果。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "book_loan")
public class Loan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "copy_id", nullable = false)
  public Long copyId;

  @Column(name = "patron_id", nullable = false)
  public Long patronId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "status", nullable = false, length = 20)
  public String status = "OPEN";

  @Column(name = "loan_date", nullable = false)
  public LocalDate loanDate;

  @Column(name = "due_date", nullable = false)
  public LocalDate dueDate;

  @Column(name = "loan_days", nullable = false)
  public int loanDays;

  @Column(name = "max_renewals", nullable = false)
  public int maxRenewals;

  @Column(name = "renewals", nullable = false)
  public int renewals = 0;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "returned_at", nullable = true)
  public Instant returnedAt;

  @Column(name = "return_condition", nullable = false, length = 20)
  public String returnCondition = "";

  @Column(name = "note", nullable = false, length = 500)
  public String note = "";

  @Column(nullable = false)
  public long version = 1;
}
