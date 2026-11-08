package com.systemX.senai;
import com.systemX.senai.config.JwtService;
import com.systemX.senai.service.PlatformService;
import com.systemX.senai.repository.*;
import com.systemX.senai.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
@SpringBootTest(properties="app.billing.simulation-enabled=true") @AutoConfigureMockMvc @Transactional
class PlatformIntegrationTest {
 @Autowired MockMvc mvc; @Autowired PlatformService platform; @Autowired UsuarioRepository users;
 @Autowired PlatformMailboxRepository mailboxes;
 @Autowired PlatformAccountRepository accounts; @Autowired JwtService jwt;
 @Autowired com.systemX.senai.service.StripeBillingService stripe;
 @Autowired com.systemX.senai.service.AuditoriaService auditoria;
 private Usuario owner(String email) { var result=platform.signup("Conta teste",email,"senha123"); return users.findById(result.id()).orElseThrow(); }
 private String token(Usuario u) { return "Bearer "+jwt.generate(u); }

 @Test void cadastroUsaEmpresaEPermiteEntrarComAsCredenciaisCriadas() throws Exception {
  mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
      .content("{\"nome\":\"Marina Silva\",\"empresa\":\"Empresa Aurora\",\"email\":\"marina@example.test\",\"senha\":\"senha123\"}"))
      .andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Marina Silva"));
  String login=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
      .content("{\"email\":\"marina@example.test\",\"senha\":\"senha123\"}"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String access=new com.fasterxml.jackson.databind.ObjectMapper().readTree(login).path("token").asText();
  mvc.perform(get("/api/platform/context").header("Authorization","Bearer "+access))
      .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Marina Silva"))
      .andExpect(jsonPath("$.account.brandName").value("Empresa Aurora"));
 }

 @Test void administradorTemCanaisPropriosSemLimiteEConsultaAuditoria() throws Exception {
  var admin=owner("admin-operacao@example.test");
  admin.setTipo(com.systemX.senai.enums.TipoUsuario.ADMINISTRADOR);
  users.saveAndFlush(admin);
  var client=owner("cliente-operacao@example.test");
  var internal=new com.systemX.senai.entity.PlatformAccount();
  internal.setNome("Operação interna");
  internal.setBrandName("SystemX");
  internal.setInternalOperations(true);
  accounts.saveAndFlush(internal);
  for(int i=0;i<4;i++) {
   mvc.perform(post("/api/platform/instances").header("Authorization",token(admin))
       .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"teste-"+i+"\"}")).andExpect(status().isCreated());
  }
  mvc.perform(get("/api/platform/operation-channels").header("Authorization",token(admin)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.instances.length()").value(4));
  mvc.perform(get("/api/platform/operation-channels").header("Authorization",token(client)))
      .andExpect(status().isForbidden());
  mvc.perform(get("/api/platform/context?accountId="+internal.getId()).header("Authorization",token(admin)))
      .andExpect(status().isNotFound());
  auditoria.registrar("Envio de teste", "Canal interno", 1L, "Aceito pelo provedor");
  mvc.perform(get("/api/auditoria/pagina?q=envio").header("Authorization",token(admin)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].acao").value("Envio de teste"));
  mvc.perform(get("/api/auditoria/pagina").header("Authorization",token(client)))
      .andExpect(status().isForbidden());
 }

 @Test void widgetExigeProfissionalERevogaSessaoQuandoChaveMuda() throws Exception {
  var owner=owner("widget-owner@example.test");
  var other=owner("widget-other@example.test");
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var origin="https://parceiro.example.test";
  var config=mapper.writeValueAsString(java.util.Map.of("origin",origin));
  var query=mapper.writeValueAsString(java.util.Map.of("origin",origin,"channel","whatsapp","days",7));
  mvc.perform(put("/api/platform/widget").header("Authorization",token(owner))
      .contentType(MediaType.APPLICATION_JSON).content(config)).andExpect(status().isForbidden());

  var account=accounts.findById(owner.getContaId()).orElseThrow();
  account.setProfessional(true);
  account.setPaidUntil(Instant.now().plusSeconds(3600));
  accounts.saveAndFlush(account);
  String created=mvc.perform(put("/api/platform/widget").header("Authorization",token(owner))
      .contentType(MediaType.APPLICATION_JSON).content(config))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String key=mapper.readTree(created).path("key").asText();
  mvc.perform(get("/api/platform/widget").header("Authorization",token(owner)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.key").doesNotExist());
  mvc.perform(post("/api/widget/session").header("X-Widget-Key",key).contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":"+other.getContaId()+"}")).andExpect(status().isUnauthorized());
  String session=mvc.perform(post("/api/widget/session").header("X-Widget-Key",key)
      .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":"+owner.getContaId()+"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(300))
      .andReturn().getResponse().getContentAsString();
  String widgetToken=mapper.readTree(session).path("token").asText();
  mvc.perform(post("/api/widget/chat").header("X-Widget-Session",widgetToken).contentType(MediaType.APPLICATION_JSON)
      .content(query.replace(origin,"https://outra.example.test"))).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/widget/chat").header("X-Widget-Session",widgetToken).contentType(MediaType.APPLICATION_JSON)
      .content(query)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  mvc.perform(put("/api/platform/widget").header("Authorization",token(owner))
      .contentType(MediaType.APPLICATION_JSON).content(config)).andExpect(status().isOk());
  mvc.perform(post("/api/widget/chat").header("X-Widget-Session",widgetToken).contentType(MediaType.APPLICATION_JSON)
      .content(query)).andExpect(status().isUnauthorized());
 }
 @Test void contasIsoladasComInstanciasIndependentes() throws Exception {
  var a=owner("tenant-a@test.com"); var b=owner("tenant-b@test.com");
  mvc.perform(get("/api/platform/context").header("Authorization",token(a))).andExpect(status().isOk()).andExpect(jsonPath("$.account.id").value(a.getContaId()));
  mvc.perform(post("/api/platform/instances").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"vendas\"}")).andExpect(status().isCreated());
  mvc.perform(post("/api/platform/instances").header("Authorization",token(b)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"vendas\"}")).andExpect(status().isCreated());
  mvc.perform(get("/api/platform/context?accountId="+a.getContaId()).header("Authorization",token(b))).andExpect(status().isNotFound());
  mvc.perform(get("/api/platform/clients").header("Authorization",token(a))).andExpect(status().isForbidden());
  mvc.perform(get("/api/usuarios").header("Authorization",token(a))).andExpect(status().isForbidden());
  assertTrue(accounts.findById(a.getContaId()).orElseThrow().getTrialEndsAt().isAfter(Instant.now().plusSeconds(259100)));
 }
 @Test void testeExpiradoBloqueiaEscritaESimulacaoLibera() throws Exception {
  var a=owner("trial@test.com"); var account=accounts.findById(a.getContaId()).orElseThrow(); account.setTrialEndsAt(Instant.now().minusSeconds(1)); accounts.saveAndFlush(account);
  mvc.perform(post("/api/platform/instances").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"suporte\"}")).andExpect(status().isPaymentRequired());
  String purchase=mvc.perform(post("/api/platform/subscription/checkout").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"PIX\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  String order=new com.fasterxml.jackson.databind.ObjectMapper().readTree(purchase).path("id").asText();
  mvc.perform(post("/api/platform/subscription/checkout/"+order+"/confirm").header("Authorization",token(a))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
  mvc.perform(post("/api/platform/instances").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"suporte\"}")).andExpect(status().isCreated());
 }
 @Test void membroNaoGerenciaContaERevogacaoInvalidaToken() throws Exception {
  var a=owner("owner-team@test.com");
  String instanceResponse=mvc.perform(post("/api/platform/instances").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"restrita\"}"))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  long instanceId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(instanceResponse).path("id").asLong();
  mvc.perform(post("/api/platform/users").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Equipe\",\"email\":\"team@test.com\",\"password\":\"senha123\"}")).andExpect(status().isCreated());
  var member=users.findByEmail("team@test.com").orElseThrow(); String old=token(member);
  mvc.perform(get("/api/platform/context").header("Authorization",old)).andExpect(status().isOk()).andExpect(jsonPath("$.users.length()").value(1));
  mvc.perform(post("/api/platform/instances").header("Authorization",old).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"proibido\"}")).andExpect(status().isForbidden());
  mvc.perform(get("/api/platform/instances/"+instanceId+"/status").header("Authorization",old)).andExpect(status().isForbidden());
  mvc.perform(put("/api/platform/users/"+member.getId()+"/active").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isNoContent());
  mvc.perform(get("/api/platform/context").header("Authorization",old)).andExpect(status().isUnauthorized());
 }

 @Test void webhookStripePublicoRejeitaAssinaturaInvalida() throws Exception {
  mvc.perform(post("/api/platform/webhooks/stripe")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Stripe-Signature", "assinatura-invalida")
      .content("{}"))
    .andExpect(status().isBadRequest());
 }

 @Test void pixStripeSoLiberaPlanoDepoisDePagoEAplicaUmaVez() throws Exception {
  var owner=owner("pix-stripe@example.test");
  var account=accounts.findById(owner.getContaId()).orElseThrow();
  account.setStripePixSessionId("cs_fixture_pix");
  account.setStripePixAmountCents(9990);
  accounts.saveAndFlush(account);

  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var checkout=mapper.createObjectNode().put("id","cs_fixture_pix")
      .put("client_reference_id",account.getId().toString()).put("mode","payment")
      .put("currency","brl").put("amount_total",9990).put("payment_status","unpaid");
  var payload=mapper.createObjectNode();
  payload.putObject("data").set("object",checkout);
  var completed=new com.stripe.model.Event();
  completed.setId("evt_fixture_pix_completed");
  completed.setType("checkout.session.completed");
  stripe.process(completed,payload.toString());
  assertFalse(account.isProfessional());

  checkout.put("payment_status","paid");
  var paid=new com.stripe.model.Event();
  paid.setId("evt_fixture_pix_paid");
  paid.setType("checkout.session.async_payment_succeeded");
  stripe.process(paid,payload.toString());
  assertTrue(account.isProfessional());
  var expires=account.getPaidUntil();
  assertEquals(expires,account.getStripePixPaidUntil());
  assertNull(account.getStripePixSessionId());
  paid.setId("evt_fixture_pix_duplicate");
  stripe.process(paid,payload.toString());
  assertEquals(expires,account.getPaidUntil());
 }

 @Test void limiteDeTesteEPagamentoPixOuCartaoComConfirmacaoIdempotente() throws Exception {
  for(String method:new String[]{"PIX","CARD"}) {
   var a=owner("quota-"+method.toLowerCase()+"@test.com"); var other=owner("other-"+method.toLowerCase()+"@test.com");
   mvc.perform(post("/api/platform/instances").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"vendas\"}")).andExpect(status().isCreated());
   mvc.perform(post("/api/platform/instances").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"suporte\"}")).andExpect(status().isPaymentRequired());
   var mailbox=new com.systemX.senai.entity.PlatformMailbox();mailbox.setContaId(a.getContaId());mailbox.setNome("Financeiro");mailbox.setEmail("mail@test.com");mailbox.setEncryptedPassword("test-encrypted");mailboxes.saveAndFlush(mailbox);
   mvc.perform(post("/api/platform/mailboxes").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Outra\",\"email\":\"other@test.com\",\"password\":\"test-only\"}")).andExpect(status().isPaymentRequired());
   var historical=accounts.findById(a.getContaId()).orElseThrow();historical.setPaidUntil(Instant.now().plusSeconds(10000));accounts.saveAndFlush(historical);
   mvc.perform(get("/api/platform/context").header("Authorization",token(a))).andExpect(jsonPath("$.subscription.tier").value("TRIAL")).andExpect(jsonPath("$.subscription.whatsappLimit").value(1));
   String purchase=mvc.perform(post("/api/platform/subscription/checkout").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\""+method+"\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn().getResponse().getContentAsString();
   String order=new com.fasterxml.jackson.databind.ObjectMapper().readTree(purchase).path("id").asText();
   mvc.perform(get("/api/platform/context").header("Authorization",token(a))).andExpect(jsonPath("$.subscription.whatsappLimit").value(1)).andExpect(jsonPath("$.subscription.gmailLimit").value(1));
   mvc.perform(post("/api/platform/subscription/checkout/"+order+"/confirm").header("Authorization",token(other))).andExpect(status().isNotFound());
   mvc.perform(post("/api/platform/subscription/checkout/"+order+"/confirm").header("Authorization",token(a))).andExpect(status().isOk());
   Instant paid=accounts.findById(a.getContaId()).orElseThrow().getPaidUntil();
   mvc.perform(post("/api/platform/subscription/checkout/"+order+"/confirm").header("Authorization",token(a))).andExpect(status().isOk());
   assertEquals(paid,accounts.findById(a.getContaId()).orElseThrow().getPaidUntil());
   mvc.perform(get("/api/platform/context").header("Authorization",token(a))).andExpect(jsonPath("$.subscription.tier").value("PRO")).andExpect(jsonPath("$.subscription.whatsappLimit").value(3)).andExpect(jsonPath("$.subscription.gmailLimit").value(2));
   for(int i=2;i<=3;i++) mvc.perform(post("/api/platform/instances").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"channel-"+i+"\"}")).andExpect(status().isCreated());
   mvc.perform(post("/api/platform/instances").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"extra\"}")).andExpect(status().isPaymentRequired());
  }
 }
}
