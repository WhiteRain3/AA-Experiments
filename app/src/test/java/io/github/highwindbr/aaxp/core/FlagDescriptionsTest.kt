package io.github.highwindbr.aaxp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlagDescriptionsTest {
    @Test fun bundledCatalogParsesAndIsWellFormed() {
        val parsed = FlagDescriptions.parse(File("src/main/assets/flag_descriptions.json").readText())
        assertTrue(parsed.size > 50)
        val risks = setOf("safe", "may_break", "experimental", "safety")
        parsed.forEach { (name, note) ->
            assertTrue("$name should look like Group__flag", name.matches(Regex("[A-Za-z]+__[a-z0-9_]+")))
            assertTrue("$name has unknown risk ${note.risk}", note.risk in risks)
            assertTrue("$name needs a title", note.title.isNotBlank())
            assertTrue("$name needs a real description", note.description.length > 20)
        }
    }

    @Test fun optionalFieldsAndIncompleteEntries() {
        val parsed = FlagDescriptions.parse("""{"flags":[
            {"name":"A__b","title":"T","description":"Something visible changes here.","risk":"safe","suggest":"true"},
            {"name":"A__c","title":"T","description":"Another visible change here.","risk":"may_break"},
            {"name":"A__d","title":"No description"}]}""")
        assertEquals(2, parsed.size)
        assertEquals("true", parsed.getValue("A__b").suggest)
        assertNull(parsed.getValue("A__c").suggest)
        assertEquals("May break", parsed.getValue("A__c").riskLabel)
    }
}
