package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.model.ClientText;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientInputTest {

    @Test
    void cleanStripsControlCharsAndFormattingCodes() {
        assertEquals("sodium", ClientText.clean("sodium"));
        assertEquals("xray[12:00] fake line", ClientText.clean("xray\n[12:00] fake line"));
        assertEquals("aPassed", ClientText.clean("§aPassed"));
        assertEquals("ab", ClientText.clean("a\u0000\t\r\u001b\u007fb"));
        assertEquals("", ClientText.clean("§\n"));
        assertNull(ClientText.clean(null));
        // ordinary non-ASCII text survives
        assertEquals("Fábrica — ü", ClientText.clean("Fábrica — ü"));
    }

    @Test
    void reportedModAndPackFieldsAreCleaned() {
        ModEntry mod = new ModEntry("free\ncam", "1.0§c", "ab\rcd", null);
        assertEquals("freecam", mod.id());
        assertEquals("1.0c", mod.version());
        assertEquals("abcd", mod.fingerprint());

        ResourcePackEntry pack = new ResourcePackEntry("file/x\n.zip", "§4Fake\nname", "", true);
        assertEquals("file/x.zip", pack.id());
        assertEquals("4Fakename", pack.displayName());
    }

    @Test
    void packIdThatIsOnlyControlCharsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourcePackEntry("§\n", "x", "", true));
    }
}
