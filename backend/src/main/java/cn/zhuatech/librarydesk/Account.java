// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import jakarta.persistence.*;
import java.time.*;

/** 登录账号与实时角色；密码散列不返回。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "account")
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "username", nullable = false, length = 60)
  public String username;

  @Column(name = "display_name", nullable = false, length = 120)
  public String displayName;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "password_hash", nullable = false, length = 100)
  public String passwordHash;

  @Column(name = "role_id", nullable = false)
  public Long roleId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
