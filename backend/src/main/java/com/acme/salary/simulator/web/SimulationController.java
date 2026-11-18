package com.acme.salary.simulator.web;

import com.acme.salary.shared.web.MoneyResponse;
import com.acme.salary.simulator.application.SimulationOutcome;
import com.acme.salary.simulator.application.SimulationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
class SimulationController {

    private final SimulationService simulations;

    SimulationController(SimulationService simulations) {
        this.simulations = simulations;
    }

    /** A POST because the inputs are a body, not because anything is stored: nothing is ever written. */
    @PostMapping
    SimulationResponse run(@RequestBody SimulationRequest request) {
        SimulationOutcome outcome = simulations.simulate(request.percent(), request.scope(), request.value());
        return new SimulationResponse(
                outcome.percent().stripTrailingZeros().toPlainString(),
                outcome.scope().type().name(),
                outcome.scope().value(),
                outcome.result().employees(),
                MoneyResponse.from(outcome.result().current()),
                MoneyResponse.from(outcome.result().additional()),
                MoneyResponse.from(outcome.result().projected()));
    }

    record SimulationRequest(String percent, String scope, String value) {}

    record SimulationResponse(
            String percent,
            String scope,
            String value,
            long employees,
            MoneyResponse currentPayroll,
            MoneyResponse additionalExpenditure,
            MoneyResponse projectedPayroll) {}
}
