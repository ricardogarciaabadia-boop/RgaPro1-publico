package com.rgapro1.ocaso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PolicyOcrParserTest {
    @Test
    public void detectsDecesosAndKeepsTomadorSeparateFromInsureds() throws Exception {
        String text = "PÓLIZA DE SEGURO DE OCASO DECESOS INTEGRAL\n"
                + "Nº de Póliza 4064289\n"
                + "Tomador del Seguro y Domicilio\nCRISTINA RODRIGUEZ JIMENEZ\nDOC. ID. 48920227D\n"
                + "RELACION DE ASEGURADOS QUE COMPONEN LA POLIZA\n"
                + "001 48920227D CRISTINA RODRIGUEZ JIMENEZ 28/03/1978 M 01/02/1985\n"
                + "002 27906367F ANGELES JIMENEZ CANTERO 10/12/1954 M 01/04/1957\n"
                + "003 49163555C EDUARDO GOMEZ RODRIGUEZ 03/09/1999 V 14/11/2003\n"
                + "GARANTIAS";
        JSONObject result = PolicyOcrParser.parse(text);
        assertEquals("Decesos Integral", result.getString("type"));
        assertEquals("4064289", result.getString("number"));
        assertEquals("CRISTINA RODRIGUEZ JIMENEZ", result.getString("holder"));
        assertEquals("48920227D", result.getString("holderDni"));
        JSONArray insureds = result.getJSONArray("insureds");
        assertEquals(3, insureds.length());
        assertEquals("27906367F", insureds.getJSONObject(1).getString("identityNumber"));
        assertEquals("ANGELES JIMENEZ CANTERO", insureds.getJSONObject(1).getString("name"));
    }

    @Test
    public void distinguishesOtherProducts() {
        assertEquals("Asistencia Familiar XXI", PolicyOcrParser.parse("PÓLIZA DE SEGURO DE ASISTENCIA FAMILIAR XXI").optString("type"));
        assertEquals("Ocaso Accidentes de la Mujer", PolicyOcrParser.parse("PÓLIZA DE SEGURO DE OCASO ACCIDENTES DE LA MUJER").optString("type"));
        assertEquals("Ocaso Ahorro Garantizado Flexible", PolicyOcrParser.parse("PÓLIZA DE SEGURO DE VIDA A PRIMA PERIODICA OCASO AHORRO GARANTIZADO FLEXIBLE").optString("type"));
        assertEquals("Ocaso Comunidades", PolicyOcrParser.parse("Condiciones Particulares Ocaso Comunidades").optString("type"));
        assertEquals("Ocaso Hogar Protección", PolicyOcrParser.parse("Condiciones Particulares Ocaso Hogar Protección").optString("type"));
        assertTrue(PolicyOcrParser.parse("Condiciones Particulares Ocaso Hogar Senior").optString("type").contains("Hogar Senior"));
    }
}
