package com.contentreg.app.feature3_text.classifier

import org.junit.Assert.*
import org.junit.Test

/** M3.1 — unit tests for [KeywordContextRules]: keyword detection, context discounts, amplifiers. */
class KeywordContextRulesTest {

    private fun score(text: String) = KeywordContextRules.score(text.lowercase())

    // ── Explicit Tier-1 keywords ──────────────────────────────────────────────────────────

    @Test
    fun `tier-1 keyword alone is above block threshold`() {
        val result = score("watch free porn videos online")
        assertTrue(result.shouldBlock)
        assertEquals("porn", result.triggeredKeyword)
    }

    @Test
    fun `xxx triggers block`() {
        val result = score("xxx amateur compilation free")
        assertTrue(result.shouldBlock)
    }

    @Test
    fun `onlyfans triggers block`() {
        assertTrue(score("subscribe to my onlyfans page").shouldBlock)
    }

    @Test
    fun `prefix match catches masturbation`() {
        val result = score("guide to masturbation")
        assertTrue(result.shouldBlock)
        assertEquals("masturbat", result.triggeredKeyword)
    }

    @Test
    fun `prefix match catches ejaculation`() {
        assertTrue(score("premature ejaculation facts").shouldBlock)
    }

    // ── Moderate Tier-2 keywords ──────────────────────────────────────────────────────────

    @Test
    fun `nude alone does not reach block threshold`() {
        val result = score("nude")
        assertFalse(result.shouldBlock)
    }

    @Test
    fun `nude with amplifiers crosses threshold`() {
        val result = score("nude teen video free watch")
        assertTrue(result.shouldBlock)
    }

    @Test
    fun `nsfw alone does not block`() {
        assertFalse(score("nsfw content warning").shouldBlock)
    }

    @Test
    fun `nsfw with video amplifier pushes over threshold`() {
        val result = score("nsfw video compilation free download")
        assertTrue(result.shouldBlock)
    }

    // ── Safe-context words reduce confidence ─────────────────────────────────────────────

    @Test
    fun `porn in recovery context is not blocked`() {
        val result = score("porn addiction recovery support group therapy")
        assertFalse(result.shouldBlock)
        assertEquals(ClassificationReason.SAFE_CONTEXT, result.reason)
    }

    @Test
    fun `pornography in medical education context is not blocked`() {
        assertFalse(score("pornography addiction medical research study statistics").shouldBlock)
    }

    @Test
    fun `nude in art museum context is not blocked`() {
        assertFalse(score("nude painting sculpture museum photography portrait").shouldBlock)
    }

    @Test
    fun `naked in wildlife documentary context is not blocked`() {
        assertFalse(score("naked mole rat wildlife nature documentary species").shouldBlock)
    }

    @Test
    fun `sex in crime news context is not blocked`() {
        assertFalse(score("sex offender arrested charged convicted criminal").shouldBlock)
    }

    @Test
    fun `sex in education context is not blocked`() {
        assertFalse(score("sex education health biology anatomy").shouldBlock)
    }

    @Test
    fun `hentai in academic definition context is not blocked`() {
        // "Wikipedia" + "definition" + "history of" should heavily discount
        assertFalse(score("hentai wikipedia definition history of academic analysis").shouldBlock)
    }

    // ── Amplifiers raise confidence ───────────────────────────────────────────────────────

    @Test
    fun `amplifiers increase confidence for moderate keyword`() {
        val withoutAmp = score("erotic")
        val withAmp = score("erotic video stream free download")
        assertTrue(withAmp.confidence > withoutAmp.confidence)
    }

    @Test
    fun `multiple amplifiers compound`() {
        val one = score("lewd video")
        val many = score("lewd video stream live cam free")
        assertTrue(many.confidence > one.confidence)
    }

    // ── Context-tier keywords ─────────────────────────────────────────────────────────────

    @Test
    fun `sex alone is below block threshold`() {
        assertFalse(score("sex").shouldBlock)
    }

    @Test
    fun `sexy alone is below block threshold`() {
        assertFalse(score("sexy").shouldBlock)
    }

    @Test
    fun `fetish alone is below block threshold`() {
        assertFalse(score("fetish").shouldBlock)
    }

    // ── Clean text ────────────────────────────────────────────────────────────────────────

    @Test
    fun `clean text returns no match`() {
        val result = score("the quick brown fox jumps over the lazy dog")
        assertFalse(result.shouldBlock)
        assertEquals(ClassificationReason.NO_MATCH, result.reason)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `empty string returns clean`() {
        assertEquals(TextClassification.CLEAN, score(""))
    }

    // ── URL path contributes to scoring ──────────────────────────────────────────────────

    @Test
    fun `explicit path segment contributes to score`() {
        // Simulating what ContextClassifier prepends: url + space + pageText
        val result = score("reddit.com/r/nsfw watch video")
        // "nsfw" (Tier.MODERATE) + "watch" + "video" amplifiers → should block
        assertTrue(result.shouldBlock)
    }

    // ── Tokenizer ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `tokenize splits on whitespace and punctuation`() {
        val tokens = KeywordContextRules.tokenize("Hello, world! Test.")
        assertTrue("hello" in tokens)
        assertTrue("world" in tokens)
        assertTrue("test" in tokens)
    }

    // ── Context window ────────────────────────────────────────────────────────────────────

    @Test
    fun `contextWindow returns correct slice`() {
        val words = listOf("a", "b", "c", "d", "e")
        val window = KeywordContextRules.contextWindow(words, center = 2, window = 1)
        assertEquals(listOf("b", "c", "d"), window)
    }

    @Test
    fun `contextWindow clamps to list bounds`() {
        val words = listOf("a", "b", "c")
        val window = KeywordContextRules.contextWindow(words, center = 0, window = 5)
        assertEquals(words, window)
    }
}