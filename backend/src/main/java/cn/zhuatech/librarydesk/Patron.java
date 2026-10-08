// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import jakarta.persistence.*;
import java.time.*;

/** 读者借阅证绑定已有账号，不复制密码或敏感证件。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "patron")
public class Patron {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "account_id", nullable = false)
  public Long accountId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "card_no", nullable = false, length = 40)
  public String cardNo;

  @Column(name = "active", nullable = false)
  public boolean active = true;

  @Column(name = "note", nullable = false, length = 500)
  public String note = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public long version = 1;
}
