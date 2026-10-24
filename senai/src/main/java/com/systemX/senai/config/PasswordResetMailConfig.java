package com.systemX.senai.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
@Configuration
public class PasswordResetMailConfig {
 @Bean(name="passwordResetMailExecutor") public ThreadPoolTaskExecutor passwordResetMailExecutor() {
  var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(1);executor.setMaxPoolSize(1);executor.setQueueCapacity(50);executor.setThreadNamePrefix("password-reset-mail-");return executor;
 }
}
