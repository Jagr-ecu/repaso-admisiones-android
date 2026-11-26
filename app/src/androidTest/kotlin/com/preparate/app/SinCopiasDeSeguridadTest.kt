package com.preparate.app

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * La base de Room y las preferencias no salen del dispositivo en una copia de seguridad del sistema (issue #61):
 * tienen el progreso y los Writing de todos los estudiantes que usan el dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class SinCopiasDeSeguridadTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val info: ApplicationInfo = contexto.packageManager.getApplicationInfo(contexto.packageName, 0)

    @Test
    fun laAppNoPermiteCopiasDeSeguridad() {
        assertEquals("android:allowBackup debe ser false", 0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun lasReglasDeExtraccionExcluyenTodo() {
        val id = contexto.resources.getIdentifier("reglas_extraccion_datos", "xml", contexto.packageName)
        assertTrue("falta res/xml/reglas_extraccion_datos.xml", id != 0)

        val excluidos = exclusiones(id)
        val dominios = setOf("root", "file", "database", "sharedpref", "external")
        for (s in listOf("cloud-backup", "device-transfer")) {
            assertTrue(
                "$s debe excluir $dominios, excluye ${excluidos[s]}",
                excluidos[s].orEmpty().containsAll(dominios),
            )
        }
    }

    /** Los dominios que cada sección (cloud-backup, device-transfer) excluye por completo. Falla si hay un include. */
    private fun exclusiones(id: Int): Map<String, Set<String>> {
        val excluidos = mutableMapOf<String, MutableSet<String>>()
        var seccion = ""
        contexto.resources.getXml(id).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG) seccion = anotar(xml, seccion, excluidos)
            }
        }
        return excluidos
    }

    /** Anota una etiqueta y devuelve la sección en la que se está. */
    private fun anotar(xml: XmlPullParser, seccion: String, excluidos: MutableMap<String, MutableSet<String>>): String {
        when (xml.name) {
            "cloud-backup", "device-transfer" -> return xml.name

            "include" -> throw AssertionError("no debe haber <include> en $seccion")

            "exclude" -> if (xml.getAttributeValue(null, "path") == ".") {
                excluidos.getOrPut(seccion) { mutableSetOf() } += xml.getAttributeValue(null, "domain")
            }
        }
        return seccion
    }
}
