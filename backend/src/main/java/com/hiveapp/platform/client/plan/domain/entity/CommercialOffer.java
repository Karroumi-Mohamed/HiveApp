package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.dto.CommercialOfferEffectSnapshot;
import com.hiveapp.platform.client.plan.dto.CommercialOfferSelection;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="commercial_offers", uniqueConstraints={
        @UniqueConstraint(name="uk_offer_lineage_revision", columnNames={"lineage_id","revision_number"}),
        @UniqueConstraint(name="uk_offer_draft_lineage", columnNames="draft_lineage_key")}, indexes={
        @Index(name="idx_offer_status_window", columnList="status,starts_at,ends_at,id"),
        @Index(name="idx_offer_lineage_status", columnList="lineage_id,status,id"),
        @Index(name="idx_offer_name", columnList="normalized_name")})
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CommercialOffer extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="lineage_id",nullable=false,updatable=false) private CommercialOfferLineage lineage;
    @Column(nullable=false,length=180) private String name;
    @Column(name="normalized_name",nullable=false,length=180) private String normalizedName;
    @Column(length=1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private CommercialOfferStatus status=CommercialOfferStatus.DRAFT;
    @Column(name="starts_at",nullable=false) private Instant startsAt;
    @Column(name="ends_at",nullable=false) private Instant endsAt;
    @Column(name="draft_lineage_key") private UUID draftLineageKey;
    @Column(name="revision_number",nullable=false,updatable=false) private int revisionNumber;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="source_offer_id",updatable=false) private CommercialOffer sourceOffer;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="exact_selection",nullable=false) private CommercialOfferSelection selection;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="effects",nullable=false) private CommercialOfferEffectSnapshot effects;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="retired_at") private Instant retiredAt;
    @Column(name="archived_at") private Instant archivedAt;
    @Version @Column(name="row_version",nullable=false) private long version;

    public static CommercialOffer draft(CommercialOfferLineage lineage,String name,String description,
            Instant startsAt,Instant endsAt,CommercialOfferSelection selection,CommercialOfferEffectSnapshot effects) {
        CommercialOffer offer=new CommercialOffer(); offer.lineage=Objects.requireNonNull(lineage);
        offer.draftLineageKey=lineage.getId(); offer.revisionNumber=1;
        offer.apply(name,description,startsAt,endsAt,selection,effects); return offer;
    }
    public void edit(String name,String description,Instant startsAt,Instant endsAt,CommercialOfferSelection selection,CommercialOfferEffectSnapshot effects){ requireDraft(); apply(name,description,startsAt,endsAt,selection,effects); }
    public CommercialOffer duplicate(CommercialOfferLineage newLineage,String duplicateName){ CommercialOffer copy=draft(newLineage,duplicateName,description,startsAt,endsAt,selection,effects); copy.sourceOffer=this; return copy; }
    public CommercialOffer revise(int revision){ if(status==CommercialOfferStatus.DRAFT||status==CommercialOfferStatus.ARCHIVED) throw new IllegalStateException("Only a published or retired Offer can be revised."); CommercialOffer next=draft(lineage,name,description,startsAt,endsAt,selection,effects); next.draftLineageKey=lineage.getId(); next.revisionNumber=revision; next.sourceOffer=this; return next; }
    public void publish(Instant now){ requireDraft(); status=CommercialOfferStatus.PUBLISHED;draftLineageKey=null;publishedAt=now; }
    public void retire(Instant now){ if(status!=CommercialOfferStatus.PUBLISHED) throw new IllegalStateException("Only a published Offer can be retired.");status=CommercialOfferStatus.RETIRED;retiredAt=now; }
    public void restore(){ if(status!=CommercialOfferStatus.RETIRED) throw new IllegalStateException("Only a retired Offer can be restored.");status=CommercialOfferStatus.PUBLISHED;retiredAt=null; }
    public void archive(Instant now){ if(status!=CommercialOfferStatus.RETIRED) throw new IllegalStateException("Only a retired Offer can be archived.");status=CommercialOfferStatus.ARCHIVED;archivedAt=now; }
    private void apply(String n,String d,Instant from,Instant to,CommercialOfferSelection sel,CommercialOfferEffectSnapshot effect){
        name=req(n,"Offer name");normalizedName=name.trim().toLowerCase(Locale.ROOT);description=blank(d); startsAt=Objects.requireNonNull(from);endsAt=Objects.requireNonNull(to);if(!to.isAfter(from))throw new IllegalArgumentException("Offer end must be after start.");selection=Objects.requireNonNull(sel);effects=Objects.requireNonNull(effect);validateEffects(effect);
    }
    public static String normalizeCode(String raw){if(raw==null||raw.isBlank())return null;String v=raw.trim().toUpperCase(Locale.ROOT);if(!v.matches("[A-Z0-9][A-Z0-9_-]{2,63}"))throw new IllegalArgumentException("Offer code format is invalid.");return v;}
    private static void validateEffects(CommercialOfferEffectSnapshot e){switch(e.discountType()){case NONE->{if(e.discountAmount()!=null||e.percentage()!=null||e.percentageCap()!=null)throw new IllegalArgumentException("A no-discount Offer cannot contain discount values.");}case FIXED->{if(e.discountAmount()==null||e.discountAmount().signum()<=0||e.percentage()!=null||e.percentageCap()!=null)throw new IllegalArgumentException("Fixed discount terms are invalid.");}case PERCENTAGE_WITH_CAP->{if(e.percentage()==null||e.percentage().signum()<=0||e.percentage().compareTo(new java.math.BigDecimal("100"))>0||e.percentageCap()==null||e.percentageCap().signum()<=0||e.discountAmount()!=null)throw new IllegalArgumentException("Percentage discount terms are invalid.");}} if(e.finiteQuotaBonuses().stream().anyMatch(v->v.quantity()<=0||v.featureCode().isBlank()||v.resource().isBlank()))throw new IllegalArgumentException("Quota bonuses must be typed, positive, and finite.");}
    private void requireDraft(){if(status!=CommercialOfferStatus.DRAFT)throw new IllegalStateException("Only a draft Offer can be edited.");}
    private static String req(String s,String label){if(s==null||s.isBlank())throw new IllegalArgumentException(label+" is required.");return s.trim();} private static String blank(String s){return s==null||s.isBlank()?null:s.trim();}
    public String getBusinessCode(){return lineage.getBusinessCode();} public UUID getLineageId(){return lineage.getId();}
    public CommercialCampaign getCampaign(){return lineage.getCampaign();} public AdminUser getOwner(){return lineage.getOwner();}
    public CommercialOfferDiscovery getDiscovery(){return lineage.getDiscovery();} public CommercialOfferAcceptance getAcceptance(){return lineage.getAcceptance();}
    public String getCustomerCodeHash(){return lineage.getCustomerCodeHash();} public Long getGlobalLimit(){return lineage.getGlobalLimit();} public Long getPerAccountLimit(){return lineage.getPerAccountLimit();}
}
