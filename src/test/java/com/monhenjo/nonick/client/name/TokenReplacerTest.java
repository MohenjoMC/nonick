package com.monhenjo.nonick.client.name;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenReplacerTest {
	@Test
	void replacesAWholeToken() {
		assertEquals("Steve: hello", TokenReplacer.replaceToken("Nick: hello", "Nick", "Steve"));
	}

	@Test
	void doesNotReplaceInsideAWord() {
		assertEquals("Nickname", TokenReplacer.replaceToken("Nickname", "Nick", "Steve"));
		assertFalse(TokenReplacer.containsToken("Nickname", "Nick"));
	}

	@Test
	void handlesPunctuationAroundTheName() {
		assertEquals("(Steve)", TokenReplacer.replaceToken("(Nick)", "Nick", "Steve"));
		assertEquals("Steve, hi", TokenReplacer.replaceToken("Nick, hi", "Nick", "Steve"));
	}

	@Test
	void leavesTheSameNameAlone() {
		assertEquals("Steve", TokenReplacer.replaceToken("Steve", "Steve", "Steve"));
	}

	@Test
	void supportsAliasesWithSpaces() {
		assertEquals("Steve is here", TokenReplacer.replaceToken("Not Steve is here", "Not Steve", "Steve"));
		assertTrue(TokenReplacer.containsToken("Not Steve is here", "Not Steve"));
	}
}
