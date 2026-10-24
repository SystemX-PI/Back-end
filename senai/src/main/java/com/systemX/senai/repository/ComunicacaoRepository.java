package com.systemX.senai.repository;
import com.systemX.senai.entity.Comunicacao;
import com.systemX.senai.enums.CanalComunicacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ComunicacaoRepository extends JpaRepository<Comunicacao, Long> {
	Optional<Comunicacao> findFirstByProcessoIdOrderByIdDesc(Long processoId);
	Optional<Comunicacao> findFirstByProcessoIdAndCanalOrderByIdDesc(Long processoId, CanalComunicacao canal);
    @org.springframework.data.jpa.repository.Query("select c from Comunicacao c where c.processo.id in :ids and c.id = (select max(c2.id) from Comunicacao c2 where c2.processo.id = c.processo.id and c2.canal = c.canal)")
    java.util.List<Comunicacao> buscarUltimasEmLote(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}