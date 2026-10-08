package com.yesus74.facturas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BrokerRepository extends JpaRepository<Broker, Long> {
}