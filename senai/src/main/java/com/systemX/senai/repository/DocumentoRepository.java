package com.systemX.senai.repository;
import com.systemX.senai.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentoRepository extends JpaRepository<Documento, Long> {}