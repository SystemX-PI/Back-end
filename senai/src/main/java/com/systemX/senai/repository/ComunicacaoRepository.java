package com.systemX.senai.repository;
import com.systemX.senai.entity.Comunicacao;
import com.systemX.senai.enums.CanalComunicacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ComunicacaoRepository extends JpaRepository<Comunicacao, Long> {
	Optional<Comunicacao> findFirstByProcessoIdOrderByIdDesc(Long processoId);
	Optional<Comunicacao> findFirstByProcessoIdAndCanalOrderByIdDesc(Long processoId, CanalComunicacao canal);
    Optional<Comunicacao> findFirstByProcessoIdAndCanalAndComunicadoOficialTrueOrderByIdAsc(
            Long processoId, CanalComunicacao canal);
    Optional<Comunicacao> findFirstByProcessoIdAndCanalOrderByIdAsc(Long processoId, CanalComunicacao canal);
    java.util.List<Comunicacao> findByProcessoIdAndChaveOperacao(Long processoId, String chaveOperacao);
    Page<Comunicacao> findByProcessoId(Long processoId, Pageable pagina);
    Optional<Comunicacao> findFirstByProcessoIdAndCanalAndMensagemOrderByIdAsc(
            Long processoId, CanalComunicacao canal, String mensagem);

    @org.springframework.data.jpa.repository.Query("select c from Comunicacao c where c.processo.id in :ids and c.id = (select max(c2.id) from Comunicacao c2 where c2.processo.id = c.processo.id and c2.canal = c.canal)")
    java.util.List<Comunicacao> buscarUltimasEmLote(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    interface NotificationStatusCount {
        com.systemX.senai.enums.StatusComunicacao getStatus();
        long getTotal();
    }

    @org.springframework.data.jpa.repository.Query("""
        select c.status as status, count(c) as total from Comunicacao c
        left join c.processo.cliente cliente
        where (:accountId is null or cliente.contaId = :accountId)
          and c.criadaEm >= :start and c.criadaEm < :end
          and (:channel is null or c.canal = :channel)
        group by c.status
        """)
    java.util.List<NotificationStatusCount> countNotificationStatuses(
        @org.springframework.data.repository.query.Param("accountId") Long accountId,
        @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
        @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
        @org.springframework.data.repository.query.Param("channel") CanalComunicacao channel);

    @org.springframework.data.jpa.repository.Query("""
        select c from Comunicacao c join fetch c.processo p left join p.cliente cliente
        where (:accountId is null or cliente.contaId = :accountId)
          and c.criadaEm >= :start and c.criadaEm < :end
          and (:channel is null or c.canal = :channel)
        order by c.criadaEm desc, c.id desc
        """)
    java.util.List<Comunicacao> findRecentNotifications(
        @org.springframework.data.repository.query.Param("accountId") Long accountId,
        @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
        @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
        @org.springframework.data.repository.query.Param("channel") CanalComunicacao channel,
        org.springframework.data.domain.Pageable limit);
}
