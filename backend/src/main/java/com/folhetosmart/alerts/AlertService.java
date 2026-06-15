package com.folhetosmart.alerts;

import com.folhetosmart.alerts.dto.AlertDto;
import com.folhetosmart.alerts.dto.AlertRequest;
import com.folhetosmart.auth.User;
import com.folhetosmart.common.ApiException;
import com.folhetosmart.common.NotFoundException;
import com.folhetosmart.products.Product;
import com.folhetosmart.products.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private final PriceAlertRepository alertRepository;
    private final ProductService productService;

    public AlertService(PriceAlertRepository alertRepository, ProductService productService) {
        this.alertRepository = alertRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public List<AlertDto> list(User user) {
        return alertRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(AlertDto::from)
                .toList();
    }

    @Transactional
    public AlertDto create(User user, AlertRequest request) {
        if (request.targetPrice() == null && !request.anyPromotion()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Define um preço-alvo ou ativa o alerta de qualquer promoção.");
        }
        Product product = productService.getEntity(request.productId());

        PriceAlert alert = new PriceAlert();
        alert.setUser(user);
        alert.setProduct(product);
        alert.setTargetPrice(request.targetPrice());
        alert.setAnyPromotion(request.anyPromotion());
        alert.setActive(true);

        return AlertDto.from(alertRepository.save(alert));
    }

    @Transactional
    public void delete(User user, UUID alertId) {
        PriceAlert alert = alertRepository.findByIdAndUserId(alertId, user.getId())
                .orElseThrow(() -> new NotFoundException("Alerta não encontrado."));
        alertRepository.delete(alert);
    }
}
