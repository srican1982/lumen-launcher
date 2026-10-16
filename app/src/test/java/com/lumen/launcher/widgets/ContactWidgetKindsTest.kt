package com.lumen.launcher.widgets

import org.junit.Assert.*
import org.junit.Test

class ContactWidgetKindsTest {
    @Test fun threeContactStylesHaveStableKeysAndSizes() {
        val contacts = WidgetKind.entries.filter { it.isContact }
        assertEquals(3, contacts.size)
        contacts.forEach {
            assertEquals(it, WidgetKind.parse(it.name))
            assertEquals(it.heightDp, WidgetContentRules.height(it.heightDp, it))
        }
        assertTrue(WidgetKind.ContactsRow.wide)
        assertFalse(WidgetKind.ContactsSingle.wide)
    }
    @Test fun contactsCannotInvokeUnrelatedMutatingCommands() {
        WidgetKind.entries.filter { it.isContact }.forEach {
            assertFalse(WidgetCommandReceiver.allowed(it, "CompleteListedTask"))
            assertFalse(WidgetCommandReceiver.allowed(it, "StartTrip"))
        }
    }
}
