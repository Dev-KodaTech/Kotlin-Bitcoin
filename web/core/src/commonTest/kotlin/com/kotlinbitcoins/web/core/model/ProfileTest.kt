package com.kotlinbitcoins.web.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileTest {
    @Test
    fun firstNameIsTheFirstWordOfTheName() {
        assertEquals("Maria", Profile("Maria Souza", "maria@example.com").firstName)
    }

    @Test
    fun firstNameIgnoresSpacesAtTheEnds() {
        assertEquals("Ana", Profile("  Ana  ", "ana@example.com").firstName)
    }

    @Test
    fun firstNameIgnoresRepeatedSpacesAndTabsBetweenWords() {
        assertEquals("Ana", Profile("\tAna \t  Lima", "ana@example.com").firstName)
    }

    @Test
    fun aBlankNameHasNoFirstName() {
        assertNull(Profile("", "x@example.com").firstName)
        assertNull(Profile("   ", "x@example.com").firstName)
    }
}
