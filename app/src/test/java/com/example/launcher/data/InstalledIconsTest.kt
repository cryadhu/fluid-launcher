package com.example.launcher.data

import org.junit.Assert.*
import org.junit.Test

class InstalledIconsTest {
    @Test fun indianBrandsNeverReceiveGenericSystemGlyphsEvenWhenPreinstalled() {
        val brands = mapOf(
            "com.phonepe.app" to "PhonePe", "com.application.zomato" to "Zomato",
            "com.grofers.customerapp" to "Blinkit", "com.axis.mobile" to "Axis Bank",
            "in.swiggy.android" to "Swiggy", "com.zeptoconsumerapp" to "Zepto",
            "com.hdfcbank.android.now" to "HDFC Bank", "com.kotak.bank.mobile" to "Kotak Bank",
            "com.csam.icici.bank.imobile" to "iMobile", "com.dreamplug.androidapp" to "CRED"
        )
        brands.forEach { (pkg, label) ->
            assertFalse(label, usesBundledSystemIcon(true, appIconStyle(pkg, label).glyph))
            assertFalse(label, usesBundledSystemIcon(false, appIconStyle(pkg, label).glyph))
        }
    }
    @Test fun everyThirdPartyAppUsesItsOwnArtworkRegardlessOfNameOrCategory() {
        AppCategory.entries.forEach { category ->
            listOf("Phone", "Bank", "Music", "Camera", "News", "नई ऐप").forEach { label ->
                assertFalse(usesBundledSystemIcon(false, appIconStyle("in.example.newbrand", label, category).glyph))
            }
        }
        assertTrue(usesBundledSystemIcon(true, "clock"))
        assertTrue(usesBundledSystemIcon(true, "settings"))
    }
}
