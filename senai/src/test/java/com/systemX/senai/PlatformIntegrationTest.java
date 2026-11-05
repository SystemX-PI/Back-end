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
 private Usuario owner(String email) { var result=platform.signup("Conta teste",email,"senha123"); return users.findById(result.id()).orElseThrow(); }
 private String token(Usuario u) { return "Bearer "+jwt.generate(u); }
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
 @Test void historicoDeNotificacoesExigeSessaoEIsolaConsultaPorConta() throws Exception {
  var a=owner("notifications-a@test.com"); var b=owner("notifications-b@test.com");
  mvc.perform(get("/api/platform/notification-history?start=2026-11-01&end=2026-11-05")
      .header("Authorization",token(a)))
    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
    .andExpect(jsonPath("$.accepted").value(0)).andExpect(jsonPath("$.items.length()").value(0));
  mvc.perform(get("/api/platform/notification-history?accountId="+b.getContaId()+"&start=2026-11-01&end=2026-11-05")
      .header("Authorization",token(a)))
    .andExpect(status().isNotFound());
  mvc.perform(get("/api/platform/notification-history?start=2026-11-05&end=2026-11-01")
      .header("Authorization",token(a)))
    .andExpect(status().isBadRequest());
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
  mvc.perform(post("/api/platform/users").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Equipe\",\"email\":\"team@test.com\",\"password\":\"senha123\"}")).andExpect(status().isCreated());
  var member=users.findByEmail("team@test.com").orElseThrow(); String old=token(member);
  mvc.perform(get("/api/platform/context").header("Authorization",old)).andExpect(status().isOk()).andExpect(jsonPath("$.users.length()").value(1));
  mvc.perform(post("/api/platform/instances").header("Authorization",old).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"proibido\"}")).andExpect(status().isForbidden());
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
