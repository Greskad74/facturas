package com.yesus74.facturas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RateConfirmationRepository extends JpaRepository<RateConfirmation, Long> {

    // Spring genera esta consulta automáticamente:
    // SELECT * FROM rate_confirmation WHERE estado = ?
    List<RateConfirmation> findByEstado(String estado);

    // SELECT * FROM rate_confirmation WHERE referencia_externa = ?
    Optional<RateConfirmation> findByReferenciaExterna(String referenciaExterna);

    // SELECT * FROM rate_confirmation WHERE broker_id = ?
    List<RateConfirmation> findByBrokerId(Long brokerId);
}