package com.calendarietto.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ItalianEventParserTest {
    // Domenica 1 novembre 2026, 16:47
    private val now = LocalDateTime.of(2026, 11, 1, 16, 47)

    private fun check(phrase: String, title: String, date: LocalDate, time: LocalTime?, minutes: Int = 60) {
        val p = ItalianEventParser.parse(phrase, now)
        assertEquals(phrase, title, p.title)
        assertEquals(phrase, date, p.date)
        if (time == null) assertNull(phrase, p.time) else assertEquals(phrase, time, p.time)
        assertEquals(phrase, minutes, p.durationMinutes)
    }

    @Test fun weekdayAndHour() = check("dentista martedì alle 15", "Dentista", LocalDate.of(2026, 11, 3), LocalTime.of(15, 0))
    @Test fun minutesInWords() = check("cena da mamma sabato alle 20 e 30", "Cena da mamma", LocalDate.of(2026, 11, 7), LocalTime.of(20, 30))
    @Test fun afternoon() = check("ricordami di chiamare Luca domani alle 3 del pomeriggio", "Chiamare Luca", LocalDate.of(2026, 11, 2), LocalTime.of(15, 0))
    @Test fun allDayBirthday() = check("compleanno di Giulia il 12 dicembre", "Compleanno di Giulia", LocalDate.of(2026, 12, 12), null)
    @Test fun range() = check("riunione dalle 10 alle 11:30 giovedì", "Riunione", LocalDate.of(2026, 11, 5), LocalTime.of(10, 0), 90)
    @Test fun halfPast() = check("aggiungi un appuntamento dal parrucchiere il 5 novembre alle 9 e mezza", "Dal parrucchiere", LocalDate.of(2026, 11, 5), LocalTime.of(9, 30))
    @Test fun tonight() = check("pizza con gli amici stasera alle 8", "Pizza con gli amici", LocalDate.of(2026, 11, 1), LocalTime.of(20, 0))
    @Test fun quarter() = check("colloquio domattina alle 10 e un quarto", "Colloquio", LocalDate.of(2026, 11, 2), LocalTime.of(10, 15))
    @Test fun numericDate() = check("veterinario 15/11 alle 17:45 per mezz'ora", "Veterinario", LocalDate.of(2026, 11, 15), LocalTime.of(17, 45), 30)
    @Test fun noon() = check("pranzo dalla nonna domenica a mezzogiorno", "Pranzo dalla nonna", LocalDate.of(2026, 11, 8), LocalTime.NOON)
    @Test fun pastTimeGoesTomorrow() = check("chiamare il dottore alle 9", "Chiamare il dottore", LocalDate.of(2026, 11, 2), LocalTime.of(9, 0))
    @Test fun standaloneEvening() = check("aperitivo venerdì sera", "Aperitivo", LocalDate.of(2026, 11, 6), LocalTime.of(20, 0))
    @Test fun hourAndHalf() = check("Corso di yoga mercoledì prossimo alle diciotto per un'ora e mezza", "Corso di yoga", LocalDate.of(2026, 11, 4), LocalTime.of(18, 0), 90)
    @Test fun oneOClock() = check("visita all'una lunedì", "Visita", LocalDate.of(2026, 11, 2), LocalTime.of(13, 0))
    @Test fun weekdayPlusDate() = check("Riunione con il capo lunedì 9 novembre alle 11", "Riunione con il capo", LocalDate.of(2026, 11, 9), LocalTime.of(11, 0))
    @Test fun eveningWithDuration() = check("palestra domani sera per un'ora e mezza", "Palestra", LocalDate.of(2026, 11, 2), LocalTime.of(20, 0), 90)
    @Test fun numberWordsTime() = check("veterinario alle quindici e trenta di venerdì", "Veterinario", LocalDate.of(2026, 11, 6), LocalTime.of(15, 30))
}
