package com.apollo.elevators.validation;

import com.apollo.elevators.dto.LiftDetails;
import com.apollo.elevators.enums.LiftType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LiftDetailsValidator
        implements ConstraintValidator<ValidLiftDetails, LiftDetails> {

    @Override
    public boolean isValid(
            LiftDetails lift,
            ConstraintValidatorContext context) {

        if (lift == null || lift.getLiftType() == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        if (lift.getLiftType() == LiftType.PASSENGER) {

            if (lift.getCapacityInPersons() == null
                    || lift.getCapacityInPersons() <= 0) {

                context.buildConstraintViolationWithTemplate(
                                "Capacity in persons is required for passenger lift"
                        )
                        .addPropertyNode("capacityInPersons")
                        .addConstraintViolation();

                return false;
            }
        }

        if (lift.getLiftType() == LiftType.GOODS
                || lift.getLiftType() == LiftType.HOSPITAL
                || lift.getLiftType() == LiftType.CAR) {

            if (lift.getCapacityInKg() == null
                    || lift.getCapacityInKg() <= 0) {

                context.buildConstraintViolationWithTemplate(
                                "Capacity in kg is required for this lift type"
                        )
                        .addPropertyNode("capacityInKg")
                        .addConstraintViolation();

                return false;
            }
        }

        return true;
    }
}
