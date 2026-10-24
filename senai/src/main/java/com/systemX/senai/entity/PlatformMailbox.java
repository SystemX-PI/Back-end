package com.systemX.senai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="platform_mailbox", uniqueConstraints=@UniqueConstraint(columnNames={"conta_id","email"})) @Getter @Setter
public class PlatformMailbox {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long contaId;
 @Column(nullable=false) private String nome;
 @Column(nullable=false) private String email;
 @Column(nullable=false, length=2000) private String encryptedPassword;
 @Column(nullable=false) private boolean verified = false;
}
