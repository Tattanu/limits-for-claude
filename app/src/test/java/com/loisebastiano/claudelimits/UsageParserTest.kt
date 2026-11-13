package com.loisebastiano.claudelimits

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageParserTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.classLoader!!.getResource(name)!!.readText())

    @Test
    fun newFormat_usesGenericLimitsList() {
        val limits = UsageParser.limits(fixture("usage_new_format.json"))
        assertEquals(listOf("session", "weekly_all"), limits.map { it.kind })
        assertEquals(38.0, limits[0].usedPercent, 0.001)
        assertEquals(59.0, limits[1].usedPercent, 0.001)
        // 2026-11-04T20:49:59.750Z
        assertEquals(1793825399750L, limits[0].resetsAt)
    }

    @Test
    fun newFormat_disabledExtraUsageIsSkipped() {
        val limits = UsageParser.limits(fixture("usage_new_format.json"))
        assertTrue(limits.none { it.kind == "extra_usage" })
    }

    @Test
    fun legacyFormat_readsFixedKeysAndSkipsNulls() {
        val limits = UsageParser.limits(fixture("usage_legacy_format.json"))
        assertEquals(listOf("session", "weekly_all", "weekly_opus", "extra_usage"), limits.map { it.kind })
        assertEquals(70.0, limits[2].usedPercent, 0.001)
        assertNull(limits[3].resetsAt)
    }

    @Test
    fun products_readWeeklyBreakdown() {
        val p = UsageParser.products(fixture("usage_new_format.json"))
        assertEquals(listOf("claude_code", "chat", "cowork", "other"), p.map { it.key })
        assertEquals(83.0, p[0].share, 0.001)
        assertEquals("Claude Code", p[0].name)
    }

    @Test
    fun products_missingBreakdownGivesEmptyList() {
        assertTrue(UsageParser.products(fixture("usage_legacy_format.json")).isEmpty())
    }

    @Test
    fun scopedLimit_keepsScopeInKind() {
        val o = JSONObject("""{"limits":[{"kind":"weekly_model","percent":10,"scope":"opus","resets_at":null}]}""")
        assertEquals("weekly_model:opus", UsageParser.limits(o).single().kind)
    }

    @Test
    fun objectScope_usesModelDisplayName() {
        // Real shape seen on claude.ai on 09/11/2026.
        val o = JSONObject("""{"limits":[{"kind":"weekly_model","percent":0,"scope":{"model":{"id":null,"display_name":"Fable"},"surface":null},"resets_at":null}]}""")
        assertEquals("weekly_model:Fable", UsageParser.limits(o).single().kind)
    }

    @Test
    fun objectScope_withoutNamesNeverShowsJson() {
        val o = JSONObject("""{"limits":[{"kind":"weekly_model","percent":3,"scope":{"model":null,"surface":null},"resets_at":null}]}""")
        assertEquals("weekly_model", UsageParser.limits(o).single().kind)
    }

    @Test
    fun objectScope_addsSurface() {
        val o = JSONObject("""{"limits":[{"kind":"weekly","percent":3,"scope":{"model":{"id":"x","display_name":"Opus"},"surface":"code"},"resets_at":null}]}""")
        assertEquals("weekly:Opus code", UsageParser.limits(o).single().kind)
    }

    @Test
    fun snapshot_roundTripsThroughJson() {
        val s = UsageParser.parse(fixture("usage_new_format.json"), "Pro")
        val back = Snapshot.fromJson(s.toJson())!!
        assertEquals(s.limits, back.limits)
        assertEquals(s.products, back.products)
        assertEquals("Pro", back.plan)
    }

    @Test
    fun plans_mapCapabilitiesToNames() {
        assertEquals("Max", Plans.label("chat,claude_max,default_claude_max_20x"))
        assertEquals("Pro", Plans.label("chat,claude_pro,default_claude_ai"))
        assertEquals("Team", Plans.label("chat,raven"))
        assertEquals("Free", Plans.label("chat,default_claude_ai"))
        assertEquals("Free", Plans.label("chat"))
        assertNull(Plans.label("api"))
        assertNull(Plans.label(null))
    }

    @Test
    fun freePlan_allNullFieldsGiveNoLimits() {
        val s = UsageParser.parse(fixture("usage_free_empty.json"), "Free")
        assertTrue(s.limits.isEmpty())
        assertTrue(s.products.isEmpty())
        assertTrue(s.usageAvailable)
    }

    @Test
    fun unavailableUsage_isKeptThroughJson() {
        val s = UsageParser.parse(null, "Free")
        assertTrue(s.limits.isEmpty())
        val back = Snapshot.fromJson(s.toJson())!!
        assertEquals(false, back.usageAvailable)
        assertEquals("Free", back.plan)
    }

    @Test
    fun oldSavedSnapshot_withoutAvailableFlag_defaultsToAvailable() {
        val back = Snapshot.fromJson("""{"limits":[],"products":[],"fetchedAt":1,"plan":null}""")!!
        assertTrue(back.usageAvailable)
    }
}
