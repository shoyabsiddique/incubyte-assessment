package com.acme.salary.simulator.application;

import com.acme.salary.simulator.domain.SimulationResult;
import com.acme.salary.simulator.domain.SimulationScope;
import java.math.BigDecimal;

public record SimulationOutcome(SimulationScope scope, BigDecimal percent, SimulationResult result) {}
