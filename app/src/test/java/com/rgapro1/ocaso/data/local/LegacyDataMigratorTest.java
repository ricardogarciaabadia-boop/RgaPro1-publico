package com.rgapro1.ocaso.data.local;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public class LegacyDataMigratorTest {
    @Test
    public void stableId_prefersExplicitRecordId() throws Exception {
        JSONObject record = new JSONObject();
        record.put("recordId", "abc-123");
        record.put("identityNumber", "12345678Z");

        assertEquals("legacy:recordId:abc-123", LegacyDataMigrator.stableId(record, 0));
    }

    @Test
    public void stableId_usesIdentityWhenNoRecordIdExists() throws Exception {
        JSONObject record = new JSONObject();
        record.put("identityNumber", "12345678Z");
        record.put("holder", "Ana García");

        assertEquals("legacy:identityNumber:12345678Z", LegacyDataMigrator.stableId(record, 4));
    }

    @Test
    public void stableId_fallsBackToIndexForUnidentifiedRecords() throws Exception {
        JSONObject record = new JSONObject();

        assertEquals("legacy:index:4", LegacyDataMigrator.stableId(record, 4));
    }
}
