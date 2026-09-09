package com.apollo.elevator.engineer.model.entity;

import com.apollo.elevator.engineer.model.enums.AnswerType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_check_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCheckItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display order within the report */
    @Column(name = "item_order", nullable = false)
    private Integer itemOrder;

    @Column(name = "question", nullable = false, length = 300)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", nullable = false, length = 20)
    private AnswerType answerType;

    /** Populated when answerType = YES_NO */
    @Column(name = "answer_yn")
    private Boolean answerYn;

    /** Populated when answerType = DESCRIPTIVE, or as additional notes for YES_NO items */
    @Column(name = "answer_text", length = 1000)
    private String answerText;
}
