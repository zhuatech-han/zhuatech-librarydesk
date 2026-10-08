// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.librarydesk;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 图书目录、读者端与柜台API；鉴权和业务守卫在服务端执行。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final CatalogService catalog;
  final CirculationService flow;
  final AdminService admin;
  final AccessService access;
  final Store db;

  /** 连接完整业务与系统目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ApiController(
      CatalogService c, CirculationService f, AdminService m, AccessService a, Store d) {
    catalog = c;
    flow = f;
    admin = m;
    access = a;
    db = d;
  }

  /** 按范围读取最小目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return catalog.options();
  }

  /** 分页检索可见书目。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/books")
  public Object books(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "latest") String sort) {
    return catalog.list(q, category, status, page, size, sort);
  }

  /** 读取书目和单册。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/books/{id}")
  public Object book(@PathVariable Long id) {
    return catalog.detail(id);
  }

  /** 新建书目。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/books")
  public Object create(@RequestBody Map<String, Object> b) {
    return catalog.saveBook(null, b);
  }

  /** 带版本修改书目。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/books/{id}")
  public Object update(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return catalog.saveBook(id, b);
  }

  /** 只删除无历史书目。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/books/{id}")
  public Object removeBook(@PathVariable Long id, @RequestParam long version) {
    catalog.deleteBook(id, version);
    return Map.of("ok", true);
  }

  /** 登记唯一单册条码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/books/{id}/copies")
  public Object copy(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return catalog.createCopy(id, b);
  }

  /** 单册架位和状态维护。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/copies/{id}")
  public Object updateCopy(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return catalog.editCopy(id, b);
  }

  /** 仅删除无历史单册。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/copies/{id}")
  public Object removeCopy(@PathVariable Long id, @RequestParam long version) {
    catalog.deleteCopy(id, version);
    return Map.of("ok", true);
  }

  /** 授权读者目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/patrons")
  public Object patrons() {
    return catalog.patrons();
  }

  /** 开办借阅证。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/patrons")
  public Object createPatron(@RequestBody Map<String, Object> b) {
    return catalog.savePatron(null, b);
  }

  /** 修改读者借阅资格。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/patrons/{id}")
  public Object updatePatron(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return catalog.savePatron(id, b);
  }

  /** 仅删除无历史读者。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/patrons/{id}")
  public Object deletePatron(@PathVariable Long id, @RequestParam long version) {
    catalog.deletePatron(id, version);
    return Map.of("ok", true);
  }

  /** 本人借阅证。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/my")
  public Object me() {
    return flow.me();
  }

  /** 精确单册条码查阅，不触发写入。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/lookup")
  public Object lookup(@RequestParam String barcode) {
    return flow.lookup(barcode);
  }

  /** 本人或授权馆员代办预约。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/holds")
  public Object hold(@RequestBody Map<String, Object> b) {
    return flow.hold(b);
  }

  /** 本人取消或馆员带原因取消预约。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/holds/{id}/cancel")
  public Object cancel(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return flow.cancelHold(id, b);
  }

  /** 显式过期释放和候补分配。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/holds/process")
  public Object process() {
    return flow.process();
  }

  /** 柜台按唯一单册借出。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/checkout")
  public Object checkout(@RequestBody Map<String, Object> b) {
    return flow.checkout(b);
  }

  /** 正常归还或异常结案。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/loans/{id}/return")
  public Object checkin(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return flow.checkin(id, b);
  }

  /** 检查候补和逾期后续借。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/loans/{id}/renew")
  public Object renew(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return flow.renew(id, b);
  }

  /** 按本人或图书馆范围检索借阅与预约。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/records/{kind}")
  public Object records(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String state,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "latest") String sort) {
    return flow.records(kind, q, state, mine, page, size, sort);
  }

  /** 实时流通统计。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object reports() {
    return flow.report();
  }

  /** 授权UTF-8借阅台账CSV。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports/export")
  public ResponseEntity<byte[]> export() {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=librarydesk-loans.csv")
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(flow.csv().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /** 仅返回所属范围的审计记录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(
            a ->
                access.department(a.departmentId)
                    && (!access.role().scope.equals("ASSIGNED")
                        || a.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent a) -> a.id).reversed())
        .toList();
  }

  /** 后台账号表单所需角色与图书馆。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/options")
  public Object adminOptions() {
    return admin.options();
  }

  /** 读取实际系统目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  public Object adminRead(@PathVariable String kind) {
    return admin.read(kind);
  }

  /** 新增可扩展系统目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object adminCreate(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 更新系统目录并保护最后管理员。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object adminUpdate(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }
}
