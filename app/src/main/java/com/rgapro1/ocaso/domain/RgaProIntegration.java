package com.rgapro1.ocaso.domain;

import com.rgapro1.ocaso.domain.duplicate.DuplicateClientGuard;
import com.rgapro1.ocaso.domain.documents.DocumentCaptureSession;
import com.rgapro1.ocaso.domain.identity.IdentityDocumentFlow;
import com.rgapro1.ocaso.domain.search.ClientSearch;
import com.rgapro1.ocaso.domain.products.ProductHistory;
import com.rgapro1.ocaso.domain.renewal.RenewalWindow;

/**
 * Single domain facade for the workflows exposed by RgaPro.
 * UI code can depend on this facade instead of knowing the individual rules.
 */
public final class RgaProIntegration {
    private RgaProIntegration() {}

    public static boolean isTwoSidedIdentity(String type) {
        return IdentityDocumentFlow.requiredSides(type) == 2;
    }

    public static boolean sameIdentity(String left, String right) {
        return IdentityDocumentFlow.sameIdentity(left, right);
    }

    public static boolean possibleDuplicate(String identity, String existingIdentity,
                                             String phone, String existingPhone) {
        return DuplicateClientGuard.sameIdentity(identity, existingIdentity)
                || DuplicateClientGuard.sameContact(phone, existingPhone);
    }

    public static boolean matchesClientField(String field, String query) {
        return ClientSearch.contains(field, query);
    }

    public static boolean isHistoricalProduct(String status) {
        return ProductHistory.isHistorical(status);
    }

    public static boolean isActiveProduct(String status) {
        return ProductHistory.isActive(status);
    }

    public static String renewalBucket(long daysRemaining) {
        return RenewalWindow.bucket(daysRemaining);
    }

    public static DocumentCaptureSession newDocumentSession() {
        return new DocumentCaptureSession();
    }
}
