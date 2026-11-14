package com.systemX.senai.service;

import com.systemX.senai.entity.*;
import com.systemX.senai.repository.*;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;

@Service
public class PlatformService {
  private static final String ACCOUNT_NOT_FOUND = "Conta não encontrada.";
  private static final String FIELD_EMAIL = "email";
 private final PlatformAccountRepository accounts;
 private final PlatformInstanceRepository instances;
 private final PlatformMailboxRepository mailboxes;
 private final UsuarioRepository users;
 private final PasswordEncoder encoder;
 private final EvolutionService evolution;
 private final PlatformCredentialService credentials;
 private final JdbcTemplate jdbc;
 private final boolean simulation;
 private final PlatformPlans plans;
 private final boolean stripeEnabled;
 private final int stripePriceCents;
 public PlatformService(PlatformAccountRepository accounts, PlatformInstanceRepository instances,
  PlatformMailboxRepository mailboxes, UsuarioRepository users, PasswordEncoder encoder,
  EvolutionService evolution, PlatformCredentialService credentials, JdbcTemplate jdbc, PlatformPlans plans,
  @Value("${app.billing.simulation-enabled:false}") boolean simulation,
  @Value("${app.billing.stripe.enabled:false}") boolean stripeEnabled,
  @Value("${app.billing.stripe.secret-key:}") String stripeSecretKey,
  @Value("${app.billing.stripe.webhook-secret:}") String stripeWebhookSecret,
  @Value("${app.billing.stripe.monthly-price-id:}") String stripePriceId,
  @Value("${app.billing.stripe.monthly-price-cents:0}") int stripePriceCents) {
  this.accounts=accounts; this.instances=instances; this.mailboxes=mailboxes; this.users=users;
  this.encoder=encoder; this.evolution=evolution; this.credentials=credentials; this.jdbc=jdbc; this.simulation=simulation&&!stripeEnabled; this.plans=plans;
  this.stripeEnabled=stripeEnabled && !stripeSecretKey.isBlank() && !stripeWebhookSecret.isBlank() && !stripePriceId.isBlank() && stripePriceCents>0;
  this.stripePriceCents=stripePriceCents;
 }
 public Usuario currentUser() {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth==null) throw error(401,"Entre para continuar.");
  return users.findByEmail(auth.getName()).filter(Usuario::isAtivo).orElseThrow(()->error(401,"Sessão inválida."));
 }
 private void admin() { if(currentUser().getTipo()!=TipoUsuario.ADMINISTRADOR) throw error(403,"Acesso exclusivo do administrador da plataforma."); }
 public PlatformAccount account(Long requested) {
  Usuario user=currentUser();
  Long id=user.getTipo()==TipoUsuario.ADMINISTRADOR ? requested : user.getContaId();
  if(id==null) throw error(400,"Selecione uma conta de cliente.");
   if(user.getTipo()!=TipoUsuario.ADMINISTRADOR && requested!=null && !requested.equals(id)) throw error(404,ACCOUNT_NOT_FOUND);
   return accounts.findById(id).filter(a->!Boolean.TRUE.equals(a.getInternalOperations()))
       .orElseThrow(()->error(404,ACCOUNT_NOT_FOUND));
 }
 public PlatformAccount internalAccount() {
   admin();
   return internalAccountForOperation();
 }
 private PlatformAccount internalAccountForOperation() {
   return accounts.findByInternalOperationsTrue().orElseThrow(()->error(503,"Canais internos indisponíveis."));
 }
 private PlatformAccount channelAccount(Long requested) {
   return requested==null && currentUser().getTipo()==TipoUsuario.ADMINISTRADOR ? internalAccount() : account(requested);
 }
 private PlatformAccount lockChannelAccount(Long requested) {
   return accounts.lockById(channelAccount(requested).getId()).orElseThrow(()->error(404,ACCOUNT_NOT_FOUND));
 }
  public PlatformAccount lockAccount(Long requested) { var a=account(requested); return accounts.lockById(a.getId()).orElseThrow(()->error(404,ACCOUNT_NOT_FOUND)); }
 private void manager() { if(currentUser().getTipo()==TipoUsuario.USUARIO || currentUser().getTipo()==TipoUsuario.FUNCIONARIO) throw error(403,"Somente o titular da conta pode alterar esta configuração."); }
 private boolean active(PlatformAccount a) { return !a.isSuspended() && (Boolean.TRUE.equals(a.getInternalOperations()) || a.getTrialEndsAt().isAfter(Instant.now()) || plans.pro(a)); }
 private void writable(PlatformAccount a) { manager(); if(!active(a)) throw error(402,a.isSuspended()?"Conta suspensa. Contate o administrador.":"Seu teste gratuito terminou. Ative a assinatura para continuar."); }
 @Transactional
 public UsuarioResponse signup(String name,String email,String password) {
   return createAccount(name, email, password);
  }
  @Transactional
  public UsuarioResponse signup(String name,String company,String email,String password) {
   String companyName=company==null?name:company.trim();
   if(company!=null && companyName.length()<2) throw error(400,"Informe o nome da empresa.");
   return createAccount(name,companyName,email,password);
  }
  private UsuarioResponse createAccount(String name,String email,String password) {
  return createAccount(name,name,email,password);
 }
 private UsuarioResponse createAccount(String name,String company,String email,String password) {
  var a=new PlatformAccount(); a.setNome(company.trim()); a.setBrandName(company.trim()); a.setOwnerEmail(email.trim().toLowerCase(Locale.ROOT)); accounts.save(a);
  var u=new Usuario(); u.setNome(name.trim()); u.setEmail(a.getOwnerEmail()); u.setSenha(encoder.encode(password)); u.setTipo(TipoUsuario.CLIENTE); u.setContaId(a.getId()); users.save(u);
  return new UsuarioResponse(u.getId(),u.getNome(),u.getEmail(),u.getTipo(),u.isAtivo());
 }
 @Transactional
   public Usuario signupWithGoogle(GoogleIdentityService.Identity identity) {
     return createGoogleAccount(identity, identity.name());
   }
   @Transactional
   public Usuario signupWithGoogle(GoogleIdentityService.Identity identity, String companyName) {
   return createGoogleAccount(identity, companyName);
  }
  private Usuario createGoogleAccount(GoogleIdentityService.Identity identity, String companyName) {
  String email=identity.email().trim().toLowerCase(Locale.ROOT);
    String normalizedCompanyName=companyName == null ? "" : companyName.trim();
    if (normalizedCompanyName.length() < 2 || normalizedCompanyName.length() > 255) {
     throw new IllegalArgumentException("Informe um nome de empresa entre 2 e 255 caracteres.");
    }
    var account=new PlatformAccount(); account.setNome(normalizedCompanyName); account.setBrandName(normalizedCompanyName); account.setOwnerEmail(email); accounts.save(account);
  var user=new Usuario(); user.setNome(identity.name()); user.setEmail(email);
  user.setSenha(encoder.encode(UUID.randomUUID()+UUID.randomUUID().toString()));
  user.setTipo(TipoUsuario.CLIENTE); user.setContaId(account.getId()); user.setFoto(identity.picture());
  user.setAuthProvider("GOOGLE"); user.setGoogleSubject(identity.subject());
  return users.save(user);
 }
 public List<Map<String,Object>> clients() {
  admin();
  return jdbc.queryForList("SELECT a.id, a.nome, a.owner_email AS email, a.trial_ends_at AS trialEndsAt, a.paid_until AS paidUntil, a.suspended, a.professional, COALESCE(i.total,0) AS instances, COALESCE(m.total,0) AS gmails, COALESCE(u.total,0) AS users FROM platform_account a LEFT JOIN (SELECT conta_id, COUNT(*) total FROM platform_instance GROUP BY conta_id) i ON i.conta_id=a.id LEFT JOIN (SELECT conta_id, COUNT(*) total FROM platform_mailbox GROUP BY conta_id) m ON m.conta_id=a.id LEFT JOIN (SELECT conta_id, COUNT(*) total FROM usuario WHERE tipo <> 'CLIENTE' GROUP BY conta_id) u ON u.conta_id=a.id WHERE a.internal_operations IS NULL ORDER BY a.id DESC LIMIT 200");
 }
  @Transactional public UsuarioResponse createClient(String name,String email,String password) { admin(); return createAccount(name,email,password); }
 public Map<String,Object> context(Long id) {
   var u=currentUser();
   var map=new LinkedHashMap<String,Object>();
   map.put("role",u.getTipo()); map.put("photo",u.getFoto()); map.put("name",u.getNome()); map.put(FIELD_EMAIL,u.getEmail()); map.put("simulationEnabled",simulation); map.put("stripeEnabled",stripeEnabled); map.put("stripeMonthlyPriceCents",stripePriceCents);
  map.put("googleLinked",u.getGoogleSubject()!=null);
  if(u.getTipo()==TipoUsuario.ADMINISTRADOR && id==null) { map.put("account",null); return map; }
  var a=account(id); map.put("subscription",plans.view(a)); map.put("account",a); map.put("active",active(a));
  map.put("instances",instances.findByContaIdOrderByIdAsc(a.getId()).stream().map(this::instanceView).toList());
  map.put("mailboxes",mailboxes.findByContaIdOrderByIdAsc(a.getId()).stream().map(this::mailboxView).toList());
  map.put("users",users.findByContaIdOrderByIdAsc(a.getId()).stream().filter(x->x.getTipo()!=TipoUsuario.CLIENTE).map(this::userView).toList());
  return map;
 }
 private Map<String,Object> instanceView(PlatformInstance i) { return Map.of("id",i.getId(),"name",i.getNome()); }
 public Map<String,Object> internalChannels() {
   var a=internalAccount();
   var map=context(null);
   map.put("operationChannels",true);
   map.put("active",active(a));
   map.put("instances",instances.findByContaIdOrderByIdAsc(a.getId()).stream().map(this::instanceView).toList());
   map.put("mailboxes",mailboxes.findByContaIdOrderByIdAsc(a.getId()).stream().map(this::mailboxView).toList());
   map.put("users",List.of());
   return map;
 }
 private Map<String,Object> mailboxView(PlatformMailbox m) { return Map.of("id",m.getId(),"name",m.getNome(),"email",m.getEmail(),"verified",m.isVerified()); }
  private Map<String,Object> userView(Usuario u) { return Map.of("id",u.getId(),"name",u.getNome(),FIELD_EMAIL,u.getEmail(),"active",u.isAtivo()); }
 @Transactional public Map<String,Object> addInstance(Long accountId,String name) {
  var a=lockChannelAccount(accountId); writable(a);
  if(!Boolean.TRUE.equals(a.getInternalOperations()) && instances.countByContaId(a.getId())>=plans.whatsappLimit(a)) throw error(402,"Limite de WhatsApp do plano atingido. Faça um upgrade para adicionar mais números.");
  var i=new PlatformInstance(); i.setContaId(a.getId()); i.setNome(name); i.setRemoteName("account-"+a.getId()+"-"+UUID.randomUUID());
  instances.saveAndFlush(i); return instanceView(i);
 }
 private PlatformInstance instance(Long accountId,Long id) { return ownedInstance(channelAccount(accountId),id); }
 private PlatformInstance ownedInstance(PlatformAccount account,Long id) { return instances.findByIdAndContaId(id,account.getId()).orElseThrow(()->error(404,"Instância não encontrada.")); }
 public List<Map<String,Object>> operationalWhatsAppSenders() {
   requireOperator();
   return instances.findByContaIdOrderByIdAsc(internalAccountForOperation().getId())
       .stream().map(this::instanceView).toList();
 }
 private void requireOperator() {
   var role = currentUser().getTipo();
   if (role != TipoUsuario.ADMINISTRADOR && role != TipoUsuario.FUNCIONARIO) {
     throw error(403, "Acesso exclusivo da operação.");
   }
 }
 public boolean operationalWhatsAppConnected(Long senderId, String defaultInstance) {
   requireOperator();
   String remoteName = senderId == null ? defaultInstance
       : ownedInstance(internalAccountForOperation(), senderId).getRemoteName();
   if (remoteName == null) return false;
   return "open".equalsIgnoreCase(evolution.getStatus(remoteName).state());
 }
 public PlatformAccount contaParaEnvio(Long id) {
   var conta=account(id);
   if(!active(conta)) throw error(402,"A conta precisa estar ativa para enviar notificações.");
   return conta;
 }
 public String remetenteWhatsApp(PlatformAccount conta, Long id) {
   return ownedInstance(conta,id).getRemoteName();
 }
 public PlatformInstance connectedInternalWhatsApp(Long id) {
   requireOperator();
   var instance=ownedInstance(internalAccountForOperation(),id);
   String state;
   try {
       state=evolution.getStatus(instance.getRemoteName()).state();
   } catch(ResponseStatusException ex) {
       throw error(503,"Não foi possível verificar a conexão do WhatsApp. Tente novamente em instantes.");
   }
   if(!"open".equalsIgnoreCase(state))
       throw error(409,"O WhatsApp selecionado está desconectado. Reconecte em Canais internos.");
   return instance;
 }
 public EvolutionStatusResponse status(Long accountId,Long id) {
   manager();
   var i=instance(accountId,id);
   var s=evolution.getStatus(i.getRemoteName()); return new EvolutionStatusResponse(i.getNome(),s.state(),s.code(),s.qrCode(),s.pairingCode());
 }
 public EvolutionStatusResponse qr(Long accountId,Long id) {
   var a=channelAccount(accountId); writable(a);
   var i=ownedInstance(a,id);
  if(!Boolean.TRUE.equals(a.getInternalOperations()) && instances.findByContaIdOrderByIdAsc(a.getId()).stream().limit(plans.whatsappLimit(a)).noneMatch(x->x.getId().equals(id))) throw error(402,"Esta instância exige um plano com mais conexões.");
  var s=evolution.generateQr(i.getRemoteName()); return new EvolutionStatusResponse(i.getNome(),s.state(),s.code(),s.qrCode(),s.pairingCode());
 }
 public void disconnect(Long accountId,Long id) { manager(); var i=instance(accountId,id); evolution.disconnect(i.getRemoteName()); }
 @Transactional public void removeInstance(Long accountId,Long id) { manager(); var i=instance(accountId,id); evolution.remove(i.getRemoteName()); instances.delete(i); }
 @Transactional public void brand(Long id,String name,String logo,String primary,String accent) {
  var a=account(id); writable(a); a.setBrandName(name.trim()); a.setLogo(logo); a.setPrimaryColor(primary); a.setAccentColor(accent); accounts.save(a);
 }
 @Transactional public Map<String,Object> addUser(Long id,String name,String email,String password) {
   var a=account(id); writable(a);
   var u=new Usuario(); u.setNome(name.trim()); u.setEmail(email.trim().toLowerCase(Locale.ROOT)); u.setSenha(encoder.encode(password)); u.setTipo(TipoUsuario.USUARIO); u.setContaId(a.getId()); users.saveAndFlush(u); return userView(u);
 }
 @Transactional public void userActive(Long accountId,Long id,boolean active) {
   var a=account(accountId); writable(a);
   var u=users.findById(id).filter(x->a.getId().equals(x.getContaId()) && x.getTipo()==TipoUsuario.USUARIO).orElseThrow(()->error(404,"Usuário não encontrado."));
  u.setAtivo(active); u.invalidarSessoes(); users.save(u);
 }
 @Transactional public Map<String,Object> addMailbox(Long accountId,String name,String email,String password) {
  var a=lockChannelAccount(accountId); writable(a);
  if(!Boolean.TRUE.equals(a.getInternalOperations()) && mailboxes.countByContaId(a.getId())>=plans.gmailLimit(a)) throw error(402,"Limite de Gmail do plano atingido. Faça um upgrade para adicionar mais contas.");
  String clean=password.replaceAll("\\s","");
  credentials.verifyGmail(email,clean);
  var m=new PlatformMailbox(); m.setContaId(a.getId()); m.setNome(name.trim()); m.setEmail(email.trim().toLowerCase(Locale.ROOT)); m.setEncryptedPassword(credentials.encrypt(clean)); m.setVerified(true); mailboxes.saveAndFlush(m); return mailboxView(m);
 }
 @Transactional public void removeMailbox(Long accountId,Long id) { manager(); var a=channelAccount(accountId); var m=mailboxes.findByIdAndContaId(id,a.getId()).orElseThrow(()->error(404,"E-mail não encontrado.")); mailboxes.delete(m); }
  @Transactional public void suspend(Long id,boolean value) { admin(); var a=accounts.findById(id).orElseThrow(()->error(404,ACCOUNT_NOT_FOUND)); a.setSuspended(value); accounts.save(a); }
 private ResponseStatusException error(int status,String message) { return new ResponseStatusException(HttpStatus.valueOf(status),message); }
}
