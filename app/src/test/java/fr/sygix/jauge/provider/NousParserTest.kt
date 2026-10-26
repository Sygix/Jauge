/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.SpendKind
import fr.sygix.jauge.model.UsageError
import org.junit.Assert.assertEquals
import org.junit.Test

class NousParserTest {

    @Test
    fun fallsBackToRollingMemberSpend() {
        val json = parserJson(
            """{"paid_service_access": {"allowed": true, "member_spend_usd": 4.0, "member_spend_cap_usd": 40.0}}""",
        )
        val spend = NousParser.parse(json, 0L).spend!!
        assertEquals(SpendKind.ROLLING_WEEK, spend.kind)
        assertEquals(10.0, spend.percent!!, 0.001)
    }

    @Test
    fun flagsDepletedAccess() {
        val json = parserJson("""{"paid_service_access": {"allowed": false}}""")
        assertEquals(UsageError.DEPLETED, NousParser.parse(json, 0L).error)
    }
}
