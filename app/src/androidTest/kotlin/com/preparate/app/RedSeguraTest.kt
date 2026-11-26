package com.preparate.app

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `network_security_config.xml` (ADR-0018): HTTP sin cifrar hacia la red de la casa, nunca hacia Firebase ni
 * Google. Se prueba con la política que aplica Android a esta app.
 */
@RunWith(AndroidJUnit4::class)
class RedSeguraTest {
    private val politica = NetworkSecurityPolicy.getInstance()

    @Test
    fun firebaseYGoogleSoloPorHttps() {
        val hosts = listOf(
            "firestore.googleapis.com",
            "identitytoolkit.googleapis.com",
            "securetoken.googleapis.com",
            "preparate-app.firebaseio.com",
            "preparate-app.firebaseapp.com",
            "www.google.com",
            "www.gstatic.com",
        )
        for (host in hosts) assertFalse(host, politica.isCleartextTrafficPermitted(host))
    }

    @Test
    fun lmStudioEnLaRedDeCasaPorHttp() {
        for (host in listOf("192.168.1.50", "10.0.0.5", "pc-casa.local", "10.0.2.2")) {
            assertTrue(host, politica.isCleartextTrafficPermitted(host))
        }
    }

    @Test
    fun unaPeticionHttpAFirestoreLaBloqueaAndroid() {
        val conexion = URL("http://firestore.googleapis.com/").openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 5_000
            conexion.responseCode
            fail("Android dejó pasar HTTP sin cifrar hacia Firestore")
        } catch (e: IOException) {
            // UnknownServiceException: "CLEARTEXT communication to … not permitted by network security policy".
            assertTrue(e.toString(), e.message.orEmpty().contains("CLEARTEXT", ignoreCase = true))
        } finally {
            conexion.disconnect()
        }
    }
}
