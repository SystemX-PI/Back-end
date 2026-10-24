package com.systemX.senai.service;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import com.systemX.senai.entity.PlatformAccount;
import java.time.Instant;
import java.util.Map;
@Component
public class PlatformPlans {
 private final int priceCents;
 public PlatformPlans(@Value("${app.billing.demo-price-cents:9990}") int priceCents) { this.priceCents=priceCents; }
 public boolean pro(PlatformAccount a) { return a.isProfessional() && a.getPaidUntil()!=null && a.getPaidUntil().isAfter(Instant.now()); }
 public int whatsappLimit(PlatformAccount a) { return pro(a)?3:1; }
 public int gmailLimit(PlatformAccount a) { return pro(a)?2:1; }
 public int priceCents() { return priceCents; }
 public Map<String,Object> view(PlatformAccount a) { return Map.of("tier",pro(a)?"PRO":"TRIAL","whatsappLimit",whatsappLimit(a),"gmailLimit",gmailLimit(a),"upgradePriceCents",priceCents,"currency","BRL"); }
}
