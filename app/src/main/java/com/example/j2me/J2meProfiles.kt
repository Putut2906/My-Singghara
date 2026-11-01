package com.example.j2me

import java.io.File

/** Persists one [J2meProfile] per game inside filesDir/j2me/&lt;id&gt;/settings.json. */
object J2meProfiles {

    const val FILE_NAME = "settings.json"

    fun fileFor(game: J2meStore.Game): File = File(game.dir, FILE_NAME)

    fun hasSaved(game: J2meStore.Game): Boolean = fileFor(game).isFile

    fun load(game: J2meStore.Game): J2meProfile {
        val saved = J2meProfile.fromFile(fileFor(game))
        if (saved != null) return saved

        val defaults = J2meProfile.defaultsFor(game)
        save(game, defaults)
        return defaults
    }

    fun save(game: J2meStore.Game, profile: J2meProfile) {
        game.dir.mkdirs()
        fileFor(game).writeText(profile.toJson().toString(2))
    }

    fun reset(game: J2meStore.Game): J2meProfile {
        fileFor(game).delete()
        return load(game)
    }
}
