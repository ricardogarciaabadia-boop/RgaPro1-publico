package com.rgapro1.ocaso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DniOcrParserTest {
    @Test
    public void readsCoreFieldsFromTypicalDniLayout() {
        String text = "DNI 28567161B\n"
                + "APELLIDOS\nYUST CRUZ\n"
                + "NOMBRE\nMANUEL\n"
                + "SEXO M   NACIONALIDAD ESP\n"
                + "NACIMIENTO 13 03 1959\n"
                + "EMISION 23 05 2024   VALIDEZ 23 05 2034\n";
        DniOcrParser.Result r = DniOcrParser.parse(text);
        assertEquals("28567161B", r.dni);
        assertEquals("YUST CRUZ", r.surname);
        assertEquals("MANUEL", r.name);
        assertEquals("13 03 1959", r.birthDate);
    }

    @Test
    public void readsAnotherLayoutWithFechaDeNacimiento() {
        String text = "DNI 44202554G\n"
                + "APELLIDOS LOPEZ PRIETO\n"
                + "NOMBRE FRANCISCO\n"
                + "SEXO M   NACIONALIDAD ESP\n"
                + "FECHA DE NACIMIENTO 10 03 1973\n"
                + "NUM SOPORTE BHL157414\n"
                + "VALIDEZ 12 07 2028\n";
        DniOcrParser.Result r = DniOcrParser.parse(text);
        assertEquals("44202554G", r.dni);
        assertEquals("LOPEZ PRIETO", r.surname);
        assertEquals("FRANCISCO", r.name);
        assertEquals("10 03 1973", r.birthDate);
    }

    @Test
    public void toleratesCommonOcrMistakesAndSpacedIdentityNumber() {
        String text = "DNI 29 785815X\n"
                + "APELLlDOS SANCHEZ MEGA\n"
                + "N0MBRE RAFAELA\n"
                + "FECHA DE NACIMlENTO 30 06 1966\n";
        DniOcrParser.Result r = DniOcrParser.parse(text);
        assertEquals("29785815X", r.dni);
        assertEquals("SANCHEZ MEGA", r.surname);
        assertEquals("RAFAELA", r.name);
        assertEquals("30 06 1966", r.birthDate);
    }

    @Test
    public void rejectsAnInvalidDniInsteadOfTakingARandomNumber() {
        String text = "DNI 12345678A\nAPELLIDOS PRUEBA TEST\nNOMBRE ANA\nNACIMIENTO 01 02 1980";
        DniOcrParser.Result r = DniOcrParser.parse(text);
        assertTrue(r.dni.isEmpty());
        assertEquals("PRUEBA TEST", r.surname);
        assertEquals("ANA", r.name);
    }
}
