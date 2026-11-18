package com.acme.salary.compensation.web;

import com.acme.salary.compensation.application.ExchangeRateEditService;
import com.acme.salary.compensation.application.ExchangeRateView;
import com.acme.salary.shared.web.ActorResolver;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange-rates")
class ExchangeRateController {

    private final ExchangeRateEditService rates;

    ExchangeRateController(ExchangeRateEditService rates) {
        this.rates = rates;
    }

    @GetMapping
    List<ExchangeRateResponse> list() {
        return rates.list().stream().map(ExchangeRateResponse::from).toList();
    }

    @PutMapping("/{currency}")
    ExchangeRateResponse update(
            @AuthenticationPrincipal Jwt token, @PathVariable String currency, @RequestBody RateRequest request) {
        return ExchangeRateResponse.from(rates.update(ActorResolver.from(token), currency, request.rate(), request.version()));
    }

    record RateRequest(String rate, Long version) {}

    record ExchangeRateResponse(String currency, String rate, long version, boolean editable) {

        static ExchangeRateResponse from(ExchangeRateView view) {
            return new ExchangeRateResponse(view.currency(), view.rate().toPlainString(), view.version(), view.editable());
        }
    }
}
