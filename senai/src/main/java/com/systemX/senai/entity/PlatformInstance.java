package com.systemX.senai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="platform_instance", uniqueConstraints=@UniqueConstraint(columnNames={"conta_id","nome"})) @Getter @Setter
public class PlatformInstance {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long contaId;
 @Column(nullable=false, length=60) private String nome;
 @Column(nullable=false, unique=true) private String remoteName;
}
