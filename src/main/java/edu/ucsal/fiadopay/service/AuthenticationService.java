package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticationService {
    private static final String BEARER_PREFIX = "Bearer FAKE-";

    private final MerchantRepository merchantRepository;

    public AuthenticationService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public Merchant authenticateMerchant(String authorizationHeader) {
        validateAuthorizationHeader(authorizationHeader);
        Long merchantId = extractMerchantId(authorizationHeader);
        Merchant merchant = findMerchant(merchantId);
        validateMerchantStatus(merchant);
        return merchant;
    }

    private void validateAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authorization header");
        }
    }

    private Long extractMerchantId(String authorizationHeader) {
        String rawId = authorizationHeader.substring(BEARER_PREFIX.length());
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid merchant ID format");
        }
    }

    private Merchant findMerchant(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Merchant not found"));
    }

    private void validateMerchantStatus(Merchant merchant) {
        if (merchant.getStatus() != Merchant.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Merchant is not active");
        }
    }
}
