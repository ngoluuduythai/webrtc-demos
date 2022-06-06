package me.amryousef.webrtc_demo

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.KeyManagementException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Created by Admin on 3/28/2017.
 */

class TLSSocketFactory @Throws(
    NoSuchAlgorithmException::class, KeyManagementException::class, KeyStoreException::class,
    CertificateException::class, IOException::class
)
constructor() : SSLSocketFactory() {

    private val sslSocketFactory: SSLSocketFactory

    init {
        val sslContext = SSLContext.getInstance(TLS)
        sslContext.init(null, trustAllCertificatesTrustManagers, null)
        sslSocketFactory = sslContext.socketFactory
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return sslSocketFactory.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return sslSocketFactory.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(
        s: Socket,
        host: String,
        port: Int,
        autoClose: Boolean
    ): SSLSocket {
        return setProtocolsEnabled(s)
    }

    @Throws(IOException::class)
    override fun createSocket(
        host: String,
        port: Int
    ): SSLSocket {
        return setProtocolsEnabled(sslSocketFactory.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int
    ): SSLSocket {
        return setProtocolsEnabled(sslSocketFactory.createSocket(host, port, localHost, localPort))
    }

    @Throws(IOException::class)
    override fun createSocket(
        host: InetAddress,
        port: Int
    ): SSLSocket {
        return setProtocolsEnabled(sslSocketFactory.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): SSLSocket {
        return setProtocolsEnabled(
            sslSocketFactory.createSocket(address, port, localAddress, localPort)
        )
    }

    @Throws(IOException::class)
    private fun setProtocolsEnabled(socket: Socket): SSLSocket {
        return if (socket is SSLSocket) {
            socket.enabledProtocols = SUPPORTED_TLS_PROTOCOLS
            socket
        } else {
            this.createSocket(socket.inetAddress, socket.port)
        }
    }

    companion object {

        private const val TLS = "TLS"
        private val SUPPORTED_TLS_PROTOCOLS = arrayOf("TLSv1.2")


        val trustAllCertificatesTrustManagers: Array<TrustManager>
            get() = arrayOf(object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String
                ) {

                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String
                ) {

                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            })
    }

}
