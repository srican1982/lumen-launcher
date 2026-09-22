package com.lumen.launcher.ui

import com.google.common.truth.Truth.assertThat
import com.lumen.launcher.data.GlassDepth
import org.junit.Test

class BackdropToneTest {

    @Test
    fun brightLakePhotoAsksForDarkGlass() {
        val tone = toneOf(Backdrop(mean = 0.588f, brightShare = 0.479f))
        assertThat(tone).isEqualTo(BackdropTone.Light)
    }

    @Test
    fun glarePatchCountsEvenWhenTheAverageIsDark() {
        val tone = toneOf(Backdrop(mean = 0.38f, brightShare = 0.25f))
        assertThat(tone).isEqualTo(BackdropTone.Light)
    }

    @Test
    fun nightPhotoKeepsWhiteGlass() {
        val tone = toneOf(Backdrop(mean = 0.18f, brightShare = 0.04f))
        assertThat(tone).isEqualTo(BackdropTone.Dark)
    }

    @Test
    fun balancedMatchesTasksFrost() {
        val balanced = GlassColors.of(GlassDepth.Balanced, BackdropTone.Dark)
        assertThat(balanced.pill.alpha).isWithin(0.01f).of(GlassDepth.Balanced.frostAlpha)
        assertThat(GlassDepth.Balanced.frostAlpha).isWithin(0.01f).of(0.239f)
    }

    @Test
    fun solidIsDarkerThanSoft() {
        val soft = GlassColors.of(GlassDepth.Soft, BackdropTone.Light)
        val solid = GlassColors.of(GlassDepth.Solid, BackdropTone.Light)
        assertThat(solid.pill.alpha).isGreaterThan(soft.pill.alpha)
    }

    @Test
    fun lightToneOnlyAddsAVeil() {
        val dark = GlassColors.of(GlassDepth.Balanced, BackdropTone.Dark)
        val light = GlassColors.of(GlassDepth.Balanced, BackdropTone.Light)
        assertThat(dark.veil.alpha).isEqualTo(0f)
        assertThat(light.veil.alpha).isGreaterThan(0f)
        assertThat(light.pill.alpha).isWithin(0.001f).of(dark.pill.alpha)
    }
}
