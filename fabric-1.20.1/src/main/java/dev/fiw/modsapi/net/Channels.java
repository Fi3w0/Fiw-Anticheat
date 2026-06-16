package dev.fiw.modsapi.net;

import net.minecraft.util.Identifier;

/** Plugin-message channels used for the join-time verification handshake. */
public final class Channels {

    /** Server → Client: 32-byte nonce challenge. */
    public static final Identifier CHALLENGE = new Identifier("fiw-mods-api", "challenge");

    /** Client → Server: reported mod list + nonce echo. */
    public static final Identifier RESPONSE = new Identifier("fiw-mods-api", "response");

    private Channels() {}
}
