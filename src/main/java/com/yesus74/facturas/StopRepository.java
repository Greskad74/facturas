package com.yesus74.facturas;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StopRepository extends JpaRepository<Stop, Long> {

    // SELECT * FROM stop WHERE rate_confirmation_id = ? ORDER BY orden
    List<Stop> findByRateConfirmationIdOrderByOrden(Long rateConfirmationId);
}