package com.apollo.elevator.billing.service;

import com.apollo.elevator.billing.model.dto.AmcPlanPriceResponse;
import com.apollo.elevator.billing.model.entity.AmcPlanPrice;
import com.apollo.elevator.billing.repository.AmcPlanPriceRepository;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.model.enums.ContractType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fixed yearly AMC price per plan (tier), per lift, before GST. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AmcPlanPriceService {

    private final AmcPlanPriceRepository repository;

    @Transactional(readOnly = true)
    public List<AmcPlanPriceResponse> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(AmcPlanPrice::getContractType))
                .map(AmcPlanPriceService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal priceOf(ContractType contractType) {
        return repository.findById(contractType)
                .map(AmcPlanPrice::getAmount)
                .orElseThrow(() -> new ResourceNotFoundException("No AMC plan price configured for " + contractType));
    }

    @Transactional
    public AmcPlanPriceResponse update(ContractType contractType, BigDecimal amount, String updatedBy) {
        AmcPlanPrice price = repository.findById(contractType)
                .orElseGet(() -> AmcPlanPrice.builder().contractType(contractType).build());
        price.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        price.setUpdatedBy(updatedBy);
        AmcPlanPrice saved = repository.save(price);
        log.info("AMC plan price updated. contractType={}, amount={}, updatedBy={}",
                contractType, saved.getAmount(),
                com.apollo.elevator.common.util.LogMaskingUtil.mask("username", updatedBy));
        return toResponse(saved);
    }

    private static AmcPlanPriceResponse toResponse(AmcPlanPrice p) {
        return new AmcPlanPriceResponse(p.getContractType(), p.getAmount(), p.getUpdatedBy(), p.getUpdatedAt());
    }
}
