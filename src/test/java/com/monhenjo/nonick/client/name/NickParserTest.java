package com.monhenjo.nonick.client.name;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NickParserTest {
	@Test
	void decoratedRealNameIsNotANickname() {
		NickParser.Parsed parsed = NickParser.parse("Steve", "Steve", "[VIP] Steve");
		assertFalse(parsed.nicked());
		assertTrue(parsed.aliases().isEmpty());
	}

	@Test
	void plainRealNameIsNotANickname() {
		NickParser.Parsed parsed = NickParser.parse("Steve", "Steve", "Steve");
		assertFalse(parsed.nicked());
	}

	@Test
	void displayNicknameIsFound() {
		NickParser.Parsed parsed = NickParser.parse("Steve", "Steve", "[VIP] xX_Nick_Xx");
		assertTrue(parsed.nicked());
		assertEquals("xX_Nick_Xx", parsed.primary());
		assertTrue(parsed.aliases().contains("xX_Nick_Xx"));
	}

	@Test
	void rewrittenProfileNameIsTheNickname() {
		NickParser.Parsed parsed = NickParser.parse("xX_Nick_Xx", "Steve", "xX_Nick_Xx");
		assertTrue(parsed.nicked());
		assertEquals("xX_Nick_Xx", parsed.primary());
	}

	@Test
	void surnameAfterTheNameIsIgnored() {
		NickParser.Parsed parsed = NickParser.parse("Steve", "Steve", "Steve [Team Red]");
		assertFalse(parsed.nicked());
	}

	@Test
	void teamTagOutsideBracketsIsIgnored() {
		NickParser.Parsed parsed = NickParser.parse("Steve", "Steve", "Steve RED");
		assertFalse(parsed.nicked());
	}

	@Test
	void nicknameWithASpaceBecomesAnAlias() {
		NickParser.Parsed parsed = NickParser.parse("Steve", "Steve", "Not Steve");
		assertTrue(parsed.nicked());
		assertTrue(parsed.aliases().contains("Not Steve"));
	}

	@Test
	void twoNamesAreNotGuessed() {
		NickParser.Parsed parsed = NickParser.parse("Steve", "Steve", "Alex Taylor");
		assertTrue(parsed.nicked());
		assertEquals(null, parsed.primary());
	}

	@Test
	void usernameRules() {
		assertTrue(NickParser.looksLikeUsername("Steve"));
		assertTrue(NickParser.looksLikeUsername("xX_Nick_Xx"));
		assertFalse(NickParser.looksLikeUsername("Not Steve"));
		assertFalse(NickParser.looksLikeUsername("ab"));
		assertFalse(NickParser.looksLikeUsername("this_name_is_too_long"));
	}
}
