@file:UseExperimental(ExperimentalStdlibApi::class)

package me.amryousef.webrtc_demo

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import io.ktor.util.*
import io.nats.client.*
import kotlinx.coroutines.*
import java.time.Duration
import javax.net.ssl.SSLContext

@RequiresApi(Build.VERSION_CODES.O)
@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class NatClient(private val natClientListener: NatClientListener) : CoroutineScope {

    private val job = Job()
    private val gson = Gson()
    override val coroutineContext = Dispatchers.IO + job
    var  natsConn: Connection? = null

    init {
        connect()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun connect() = launch {
        println("Nats.connect")

        val natsConnectionProperties = BasicAuthNatsConnectionProperties()
        natsConnectionProperties.host = "nats://demo.nats.io:4222"
        natsConnectionProperties.connectionName = "Nats EyeCast"
        natsConnectionProperties.connectionTimeout = 5
        natsConnectionProperties.maxReconnects = -1
        natsConnectionProperties.pingInterval = 10
        natsConnectionProperties.reconnectWait = 1
        natsConnectionProperties.password = ""
        natsConnectionProperties.username = ""


        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, TLSSocketFactory.trustAllCertificatesTrustManagers, null)

        var builder  = Options.Builder()
            .server(natsConnectionProperties.host)
            .maxReconnects(natsConnectionProperties.maxReconnects)
            .connectionName(natsConnectionProperties.connectionName)
            .connectionTimeout(Duration.ofSeconds(6))
            .sslContext(sslContext)
            .traceConnection()

        builder =
            builder.connectionListener { _: Connection?, type: ConnectionListener.Events ->
                println(
                    "Status change $type"
                )
            }

        builder = builder.errorListener(object : ErrorListener {
            override fun slowConsumerDetected(conn: Connection, consumer: Consumer) {
                println("NATS connection slow consumer detected")
            }

            override fun exceptionOccurred(conn: Connection, exp: Exception) {
                println("NATS connection exception occurred")
                exp.printStackTrace()
            }

            override fun errorOccurred(conn: Connection, error: String) {
                println("NATS connection error occurred $error")
            }
        })

        val op =  builder.build()
        natsConn = Nats.connect(op)


        val natsDispatcher = natsConn?.createDispatcher { message ->
            println("xxxx message ${message.data.decodeToString()}")
        }
        natsDispatcher?.subscribe("test_topic")
        natsConn?.publish("test_topic", "Hello world".toByteArray())
        launch (Dispatchers.Main) {
            natClientListener.onConnectionEstablished()
        }
    }
}

interface NatClientListener {
    fun onConnectionEstablished()
}
