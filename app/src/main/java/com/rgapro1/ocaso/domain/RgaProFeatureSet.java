package com.rgapro1.ocaso.domain;

import com.rgapro1.ocaso.domain.documents.DocumentCaptureSession;
import com.rgapro1.ocaso.domain.duplicate.DuplicateClientGuard;
import com.rgapro1.ocaso.domain.identity.IdentityDocumentFlow;
import com.rgapro1.ocaso.domain.renewal.RenewalWindow;
import com.rgapro1.ocaso.domain.search.ClientSearch;

/** Central, Android-free facade for the features used by the RgaPro UI. */
public final class RgaProFeatureSet {
    private RgaProFeatureSet() {}

    public static int identitySides(String type) {
        return IdentityDocumentFlow.requiredSides(type);
    }

    public static boolean sameIdentity(String first, String second) {
        return IdentityDocumentFlow.sameIdentity(first, second);
    }

    public static boolean searchMatches(String field, String query) {
        return ClientSearch.contains(field, query);
    }

    public static boolean possibleDuplicate(String identity, String existingIdentity) {
        return DuplicateClientGuard.sameIdentity(identity, existingIdentity);
    }

    public static String renewalBucket(long daysRemaining) {
        return RenewalWindow.bucket(daysRemaining);
    }

    public static DocumentCaptureSession newDocumentSession() {
        return new DocumentCaptureSession();
    }
}
