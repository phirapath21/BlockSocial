package com.example.btcwidget.nostr.core

/**
 * Well-known people in Bitcoin and Nostr, offered to new accounts before the topic search
 * finishes. Each is shown only when the profile at that key has a matching name, so a key
 * that's wrong or reused never puts a stranger in front of a new user.
 */
object StarterPeople {
    class Known(val pubkey: String, private vararg val names: String) {
        fun matches(p: Profile): Boolean {
            val all = listOfNotNull(p.name, p.displayName, p.nip05).joinToString(" ").lowercase()
            return names.any { it.lowercase() in all }
        }
    }

    val LIST = listOf(
        Known("82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2", "jack"),
        Known("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d", "fiatjaf"),
        Known("04c915daefee38317fa734444acee390a8269fe5810b2241e5e6dd343dfbecc9", "odell"),
        Known("32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245", "jb55", "will"),
        Known("6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93", "gigi"),
        Known("eab0e756d32b80bcd464f3d844b8040303075a13eabc3599a762c9ac7ab91f4f", "lyn"),
        Known("85080d3bad70ccdcd7f74c29a44f55bb85cbcd3dd0cbb957da1d215bdb931204", "preston"),
        Known("84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240", "snowden"),
        Known("3f770d65d3a764a9c5cb503ae123e62ec7598ad035d836e2a810f3877a745b24", "derek"),
        Known("fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52", "pablo"),
        Known("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", "vitor"),
        Known("d61f3bc5b3eb4400efdae6169a5c17cabf3246b514361de939ce4a1a0da6ef4a", "miljan"),
    )
}
