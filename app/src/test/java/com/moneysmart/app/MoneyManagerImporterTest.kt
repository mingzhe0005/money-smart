package com.moneysmart.app

import com.moneysmart.app.data.repository.MoneyManagerImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MoneyManagerImporterTest {

    @Test
    fun excelSerialDateConvertsToExpectedLocalDate() {
        val zone = ZoneId.of("UTC")
        val millis = MoneyManagerImporter.excelSerialToEpochMillis(46347.5, zone)
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        assertEquals(LocalDate.of(2026, 11, 21), dt.toLocalDate())
        assertEquals(12, dt.hour)
    }

    @Test
    fun parseXlsxRowsReadsSharedStringsAndInlineNumbers() {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <si><t>Date</t></si>
              <si><t>Account</t></si>
              <si><t>Category</t></si>
              <si><t>Note</t></si>
              <si><t>OCBC</t></si>
              <si><t>🍿Snacks</t></si>
              <si><t>luckin coffee</t></si>
            </sst>
        """.trimIndent()

        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                  <c r="C1" t="s"><v>2</v></c>
                  <c r="E1" t="s"><v>3</v></c>
                </row>
                <row r="2">
                  <c r="A2"><v>46347.63</v></c>
                  <c r="B2" t="s"><v>4</v></c>
                  <c r="C2" t="s"><v>5</v></c>
                  <c r="E2" t="s"><v>6</v></c>
                  <c r="J2"><v>3.58</v></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(sharedStringsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val rows = MoneyManagerImporter.parseSpreadsheetOrCsv(baos.toByteArray())
        assertEquals(2, rows.size)
        assertEquals("Date", rows[0][0])
        assertEquals("OCBC", rows[1][1])
        assertEquals("🍿Snacks", rows[1][2])
        assertEquals("luckin coffee", rows[1][4])
        assertEquals("3.58", rows[1][9])
        assertTrue(MoneyManagerImporter.columnRefToIndex("K2") == 10)
    }
}
