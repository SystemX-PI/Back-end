package com.systemX.senai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.systemX.senai.service.PlatformService;
import com.systemX.senai.service.ProfileService;
import com.systemX.senai.service.NotificationHistoryService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
@RestController
@RequestMapping("/api/platform")
@Tag(name = "Plataforma", description = "Contas de clientes, equipe, perfil, identidade visual e canais de comunicação. A cobrança fica em Assinaturas.")
public class PlataformaController {
 private final PlatformService service;
 private final ProfileService profile;
 private final NotificationHistoryService notifications;

 public PlataformaController(PlatformService service, ProfileService profile, NotificationHistoryService notifications) {
  this.service = service;
  this.profile = profile;
  this.notifications = notifications;
 }
 public record Person(@NotBlank @Size(max=120) String name,@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(min=6,max=100) String password) {}
 public record Instance(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,60}") String name) {}
 public record Mailbox(@NotBlank @Size(max=120) String name,@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(max=200) String password) {}
 public record Brand(@NotBlank @Size(max=120) String name,@Size(max=400000) @Pattern(regexp="^(data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+)?$") String logo,@NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String primary,@NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String accent) {}
 public record Active(boolean active) {}
 @Operation(summary = "Consultar o contexto da conta", description = "Retorna perfil, conta, equipe, canais e limites do plano.")
 @GetMapping("/context") public Object context(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId) { return service.context(accountId); }
 @Operation(summary = "Consultar o histórico de notificações", description = "Filtra por data e canal e retorna totais por estado atual, com até 20 registros recentes. Aceite pelo provedor não confirma entrega.")
 @GetMapping("/notification-history") public com.systemX.senai.dto.NotificationHistoryResponse notificationHistory(
   @Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,
   @RequestParam java.time.LocalDate start,
   @RequestParam java.time.LocalDate end,
   @RequestParam(required=false) com.systemX.senai.enums.CanalComunicacao channel) {
  return notifications.history(service.account(accountId).getId(), start, end, channel);
 }
 @Operation(summary = "Listar contas de clientes", description = "Acesso exclusivo da administração da plataforma.")
 @GetMapping("/clients") public Object clients() { return service.clients(); }
 @Operation(summary = "Criar conta de cliente", description = "Acesso exclusivo da administração da plataforma.")
 @PostMapping("/clients") @ResponseStatus(HttpStatus.CREATED) public com.systemX.senai.dto.UsuarioResponse client(@Valid @RequestBody Person p) { return service.createClient(p.name(),p.email(),p.password()); }
 @Operation(summary = "Ativar ou suspender conta de cliente", description = "Acesso exclusivo da administração da plataforma.")
 @PutMapping("/clients/{id}/active") @ResponseStatus(HttpStatus.NO_CONTENT) public void clientActive(@PathVariable Long id,@RequestBody Active p) { service.suspend(id,!p.active()); }
 @Operation(summary = "Cadastrar uma instância de WhatsApp", description = "Respeita o limite do plano da conta.")
 @PostMapping("/instances") @ResponseStatus(HttpStatus.CREATED) public Object instance(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@Valid @RequestBody Instance p) { return service.addInstance(accountId,p.name()); }
 @Operation(summary = "Consultar a conexão do WhatsApp", description = "Consulta o estado da instância da conta.")
 @GetMapping("/instances/{id}/status") public com.systemX.senai.dto.EvolutionStatusResponse status(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@PathVariable Long id) { return service.status(accountId,id); }
 @Operation(summary = "Gerar código QR para conectar o WhatsApp")
 @PostMapping("/instances/{id}/qr") public com.systemX.senai.dto.EvolutionStatusResponse qr(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@PathVariable Long id) { return service.qr(accountId,id); }
 @Operation(summary = "Desconectar uma instância de WhatsApp")
 @PostMapping("/instances/{id}/disconnect") @ResponseStatus(HttpStatus.NO_CONTENT) public void disconnect(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@PathVariable Long id) { service.disconnect(accountId,id); }
 @Operation(summary = "Remover uma instância de WhatsApp")
 @DeleteMapping("/instances/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeInstance(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@PathVariable Long id) { service.removeInstance(accountId,id); }
 @Operation(summary = "Atualizar a identidade visual da conta")
 @PutMapping("/brand") @ResponseStatus(HttpStatus.NO_CONTENT) public void brand(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@Valid @RequestBody Brand p) { service.brand(accountId,p.name(),p.logo(),p.primary(),p.accent()); }
 @Operation(summary = "Criar um usuário na equipe")
 @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) public Object user(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@Valid @RequestBody Person p) { return service.addUser(accountId,p.name(),p.email(),p.password()); }
 @Operation(summary = "Ativar ou desativar usuário da equipe")
 @PutMapping("/users/{id}/active") @ResponseStatus(HttpStatus.NO_CONTENT) public void userActive(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@PathVariable Long id,@RequestBody Active p) { service.userActive(accountId,id,p.active()); }
 @Operation(summary = "Cadastrar um remetente Gmail", description = "Verifica as credenciais do remetente antes de salvar.")
 @PostMapping("/mailboxes") @ResponseStatus(HttpStatus.CREATED) public Object mailbox(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@Valid @RequestBody Mailbox p) { return service.addMailbox(accountId,p.name(),p.email(),p.password()); }
 @Operation(summary = "Remover um remetente Gmail")
 @DeleteMapping("/mailboxes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeMailbox(@Parameter(description = "Conta selecionada pela administração. Para usuários da conta, o servidor usa a conta da sessão.") @RequestParam(required=false) Long accountId,@PathVariable Long id) { service.removeMailbox(accountId,id); }
 public record Profile(@NotBlank @Size(max=120) String name,@Size(max=400000) String photo) {}
 public record PasswordChange(@NotBlank @Size(max=100) String currentPassword,@NotBlank @Size(min=10,max=72) String newPassword) {}
 @Operation(summary = "Atualizar o próprio perfil", description = "Altera nome e foto do usuário autenticado.")
 @PutMapping("/profile") @ResponseStatus(HttpStatus.NO_CONTENT) public void updateProfile(@Valid @RequestBody Profile p) { profile.update(p.name(),p.photo()); }
 @Operation(summary = "Alterar a própria senha", description = "Valida a senha atual e retorna uma nova sessão após a alteração.")
 @PutMapping("/profile/password") public com.systemX.senai.dto.AuthResponse password(@Valid @RequestBody PasswordChange p) { return profile.changePassword(p.currentPassword(),p.newPassword()); }
}
