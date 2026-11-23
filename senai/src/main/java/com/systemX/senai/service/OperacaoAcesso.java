package com.systemX.senai.service;

import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class OperacaoAcesso {
    private final AuditoriaService auditoria;

    public OperacaoAcesso(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public Specification<ProcessoContemplacao> processosVisiveis() {
        Long conta = auditoria.exigirOperador().getContaId();
        return (root, query, cb) -> conta == null ? cb.conjunction()
                : cb.equal(root.join("cliente", JoinType.INNER).get("contaId"), conta);
    }

    public void conferirProcesso(ProcessoContemplacao processo) {
        conferirProcesso(auditoria.exigirOperador(), processo);
    }

    public void conferirProcesso(Usuario operador, ProcessoContemplacao processo) {
        Long conta = operador.getContaId();
        var cliente = processo.getCliente();
        if (conta != null && (cliente == null || !conta.equals(cliente.getContaId()))) {
            throw new ResourceNotFoundException("Processo não encontrado: " + processo.getId());
        }
    }
}
