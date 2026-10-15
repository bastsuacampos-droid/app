package cl.sacyr.rutaitata.excavaciones.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionesTest {

    @Test
    fun comparaNumeroPorNumero() {
        assertTrue(Versiones.esMasNueva("1.1", "1.0"))
        assertTrue(Versiones.esMasNueva("1.10", "1.9"))
        assertTrue(Versiones.esMasNueva("2.0", "1.12.3"))
        assertTrue(Versiones.esMasNueva("1.1.1", "1.1"))
        assertFalse(Versiones.esMasNueva("1.1", "1.1"))
        assertFalse(Versiones.esMasNueva("1.0", "1.1"))
        assertFalse(Versiones.esMasNueva("1.1.0", "1.1"))
    }

    private fun release(tag: String, apk: String, draft: Boolean = false, prerelease: Boolean = false) = """
        {"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease, "body": "Notas $tag\r\n",
         "assets": [
           {"name": "notas.txt", "browser_download_url": "https://x/notas.txt", "size": 10},
           {"name": "$apk", "browser_download_url": "https://x/$apk", "size": 7000000}
         ]}
    """

    @Test
    fun eligeLaVersionMasNuevaDeEstaApp() {
        val json = "[" + listOf(
            // El repositorio también publica otras apps (Bitácora Vial, charlas): se ignoran.
            release("v2.19", "bitacora-vial-v2.19-debug.apk"),
            release("charlas-v1.20", "charlas-ruta-itata-1.20.apk"),
            release("excavaciones-v1.2", "excavaciones-ruta-itata-1.2.apk"),
            release("excavaciones-v1.10", "excavaciones-ruta-itata-1.10.apk"),
            release("excavaciones-v1.11", "excavaciones-ruta-itata-1.11.apk", draft = true),
            release("excavaciones-v1.12", "excavaciones-ruta-itata-1.12.apk", prerelease = true),
            release("excavaciones-v1.13", "otra-cosa.apk"),
        ).joinToString() + "]"
        assertEquals(
            VersionPublicada("1.10", "Notas excavaciones-v1.10", "https://x/excavaciones-ruta-itata-1.10.apk", 7000000),
            Versiones.leerReleases(json),
        )
    }

    @Test
    fun sinReleasesDeEstaAppNoHayVersion() {
        assertNull(Versiones.leerReleases("[" + release("v2.19", "bitacora-vial-v2.19-debug.apk") + "]"))
        assertNull(Versiones.leerReleases("[" + release("charlas-v1.3", "charlas-ruta-itata-1.3.apk") + "]"))
        assertNull(Versiones.leerReleases("[]"))
        assertNull(Versiones.leerReleases("no es json"))
    }
}
