package com.systemX.senai.repository;
import com.systemX.senai.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UsuarioRepository extends JpaRepository<Usuario, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Usuario> {
	Optional<Usuario> findByEmail(String email);
	Optional<Usuario> findFirstByAtivoTrue();
    long countByTipoAndAtivoTrue(com.systemX.senai.enums.TipoUsuario tipo);
    boolean existsByTipo(com.systemX.senai.enums.TipoUsuario tipo);
    java.util.List<Usuario> findByContaIdOrderByIdAsc(Long contaId);
}