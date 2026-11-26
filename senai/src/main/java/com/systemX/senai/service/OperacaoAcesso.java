package com.systemX.senai.service;

import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class OperacaoAcesso {
    private final AuditoriaService auditoria;
    private final PlatformService plataforma;

    public OperacaoAcesso(AuditoriaService auditoria, PlatformService plataforma) {
        this.auditoria = auditoria;
        this.plataforma = plataforma;
    }

    public Usuario operador() {
        var usuario = plataforma.currentUser();
        if (usuario.getTipo() != TipoUsuario.CLIENTE) return auditoria.exigirOperador();
        plataforma.contaTitular(usuario);
        return usuario;
    }

    public void conferirEscrita(Usuario operador) {
        if (operador.getTipo() == TipoUsuario.CLIENTE) plataforma.exigirContaOperacional(operador);
    }

    public Specification<ProcessoContemplacao> processosVisiveis() {
        Long conta = operador().getContaId();
        return (root, query, cb) -> conta == null ? cb.conjunction()
                : cb.equal(root.join("cliente", JoinType.INNER).get("contaId"), conta);
    }

    public void conferirProcesso(ProcessoContemplacao processo) {
        conferirProcesso(operador(), processo);
    }

    public void conferirProcesso(Usuario operador, ProcessoContemplacao processo) {
        Long conta = operador.getContaId();
        var cliente = processo.getCliente();
        if (conta != null && (cliente == null || !conta.equals(cliente.getContaId()))) {
            throw new ResourceNotFoundException("Processo não encontrado: " + processo.getId());
        }
    }

    public static Long contaDoProcesso(ProcessoContemplacao processo) {
        return processo.getCliente() == null ? null : processo.getCliente().getContaId();
    }
}
