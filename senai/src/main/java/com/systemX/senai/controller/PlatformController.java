package com.systemX.senai.controller;
import com.systemX.senai.service.PlatformService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
@RestController @RequestMapping("/api/platform")
public class PlatformController {
 private final PlatformService service;
 private final com.systemX.senai.service.PlatformCheckoutService checkout;
 public PlatformController(PlatformService service,com.systemX.senai.service.PlatformCheckoutService checkout) { this.service=service; this.checkout=checkout; }
 public record Person(@NotBlank @Size(max=120) String name,@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(min=6,max=100) String password) {}
 public record Instance(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,60}") String name) {}
 public record Mailbox(@NotBlank @Size(max=120) String name,@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(max=200) String password) {}
 public record Brand(@NotBlank @Size(max=120) String name,@Size(max=400000) @Pattern(regexp="^(data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+)?$") String logo,@NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String primary,@NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String accent) {}
 public record Active(boolean active) {}
 @GetMapping("/context") public Object context(@RequestParam(required=false) Long accountId) { return service.context(accountId); }
 @GetMapping("/clients") public Object clients() { return service.clients(); }
 @PostMapping("/clients") @ResponseStatus(HttpStatus.CREATED) public Object client(@Valid @RequestBody Person p) { return service.createClient(p.name(),p.email(),p.password()); }
 @PutMapping("/clients/{id}/active") @ResponseStatus(HttpStatus.NO_CONTENT) public void clientActive(@PathVariable Long id,@RequestBody Active p) { service.suspend(id,!p.active()); }
 @PostMapping("/instances") @ResponseStatus(HttpStatus.CREATED) public Object instance(@RequestParam(required=false) Long accountId,@Valid @RequestBody Instance p) { return service.addInstance(accountId,p.name()); }
 @GetMapping("/instances/{id}/status") public Object status(@RequestParam(required=false) Long accountId,@PathVariable Long id) { return service.status(accountId,id); }
 @PostMapping("/instances/{id}/qr") public Object qr(@RequestParam(required=false) Long accountId,@PathVariable Long id) { return service.qr(accountId,id); }
 @PostMapping("/instances/{id}/disconnect") @ResponseStatus(HttpStatus.NO_CONTENT) public void disconnect(@RequestParam(required=false) Long accountId,@PathVariable Long id) { service.disconnect(accountId,id); }
 @DeleteMapping("/instances/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeInstance(@RequestParam(required=false) Long accountId,@PathVariable Long id) { service.removeInstance(accountId,id); }
 @PutMapping("/brand") @ResponseStatus(HttpStatus.NO_CONTENT) public void brand(@RequestParam(required=false) Long accountId,@Valid @RequestBody Brand p) { service.brand(accountId,p.name(),p.logo(),p.primary(),p.accent()); }
 @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) public Object user(@RequestParam(required=false) Long accountId,@Valid @RequestBody Person p) { return service.addUser(accountId,p.name(),p.email(),p.password()); }
 @PutMapping("/users/{id}/active") @ResponseStatus(HttpStatus.NO_CONTENT) public void userActive(@RequestParam(required=false) Long accountId,@PathVariable Long id,@RequestBody Active p) { service.userActive(accountId,id,p.active()); }
 @PostMapping("/mailboxes") @ResponseStatus(HttpStatus.CREATED) public Object mailbox(@RequestParam(required=false) Long accountId,@Valid @RequestBody Mailbox p) { return service.addMailbox(accountId,p.name(),p.email(),p.password()); }
 @DeleteMapping("/mailboxes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeMailbox(@RequestParam(required=false) Long accountId,@PathVariable Long id) { service.removeMailbox(accountId,id); }
 public record Checkout(@NotBlank @Pattern(regexp="PIX|CARD") String paymentMethod) {}
 @PostMapping("/subscription/checkout") @ResponseStatus(HttpStatus.CREATED) public Object checkout(@RequestParam(required=false) Long accountId,@Valid @RequestBody Checkout p) { return checkout.create(accountId,p.paymentMethod()); }
 @PostMapping("/subscription/checkout/{id}/confirm") public Object confirm(@RequestParam(required=false) Long accountId,@PathVariable String id) { return checkout.confirm(accountId,id); }
}
