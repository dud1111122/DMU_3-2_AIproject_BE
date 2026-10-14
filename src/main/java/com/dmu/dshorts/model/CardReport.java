package com.dmu.dshorts.model;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="card_reports")
public class CardReport {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private SharedCard card;
 @Column(nullable=false,length=120) private String reason; @Column(nullable=false) private Instant createdAt=Instant.now();
 protected CardReport(){} public CardReport(SharedCard c,String r){card=c;reason=r;}
}
