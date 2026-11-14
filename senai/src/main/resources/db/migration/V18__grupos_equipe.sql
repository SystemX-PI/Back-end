CREATE TABLE equipe_grupo (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 conta_id BIGINT NOT NULL,
 nome VARCHAR(100) NOT NULL,
 UNIQUE (conta_id, nome),
 FOREIGN KEY (conta_id) REFERENCES platform_account(id)
);
CREATE TABLE equipe_grupo_usuario (
 grupo_id BIGINT NOT NULL,
 usuario_id BIGINT NOT NULL,
 PRIMARY KEY (grupo_id, usuario_id),
 FOREIGN KEY (grupo_id) REFERENCES equipe_grupo(id) ON DELETE CASCADE,
 FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);
