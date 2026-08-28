package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity @Table(name="commercial_offer_code_reservations",uniqueConstraints={
        @UniqueConstraint(name="uk_offer_reserved_customer_code",columnNames="normalized_code_hash"),
        @UniqueConstraint(name="uk_offer_reserved_lineage",columnNames="lineage_id")})
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CommercialOfferCodeReservation extends BaseEntity {
    @Column(name="normalized_code_hash",nullable=false,updatable=false,length=64) private String normalizedCodeHash;
    @Column(name="lineage_id",nullable=false,updatable=false) private UUID lineageId;
    public static CommercialOfferCodeReservation reserveHash(String hash,UUID lineage){var r=new CommercialOfferCodeReservation();r.normalizedCodeHash=hash;r.lineageId=lineage;return r;}
}
