package com.hiveapp.platform.client.plan.domain.repository;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferLineage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CommercialOfferLineageRepository extends JpaRepository<CommercialOfferLineage,UUID>{boolean existsByBusinessCode(String code);}
