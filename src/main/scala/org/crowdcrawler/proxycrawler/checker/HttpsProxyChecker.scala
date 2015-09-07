package org.crowdcrawler.proxycrawler.checker

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate

import org.apache.http.HttpHost
import org.apache.http.annotation.ThreadSafe
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.ssl.{NoopHostnameVerifier, SSLConnectionSocketFactory}
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.{TrustStrategy, SSLContexts}
import org.apache.http.util.EntityUtils


@ThreadSafe
object HttpsProxyChecker extends AbstractProxyChecker {
  // trust all certificates including self-signed certificates
  private[checker] val SSL_CONTEXT = SSLContexts.custom().loadTrustMaterial(null, new TrustStrategy() {
    def isTrusted(chain: Array[X509Certificate], authType: String) = true
  }).build()
  private val CLIENT = {
    val connectionFactory = new SSLConnectionSocketFactory(SSL_CONTEXT, NoopHostnameVerifier.INSTANCE)
    HttpClients.custom().setSSLSocketFactory(connectionFactory).setMaxConnTotal(AbstractProxyChecker.MAX_CONN)
      .disableRedirectHandling().build()
  }
  private val TARGET_URL = new URI("https://www.google.com")


  def check(host: String, port: Int): (Int, Int) = {
    val request = new HttpGet(TARGET_URL)
    AbstractProxyChecker.configureRequest(request, Some(new HttpHost(host, port, "http")))

    val response = CLIENT.execute(request)

    val statusCode = response.getStatusLine.getStatusCode
    val html = EntityUtils.toString(response.getEntity, StandardCharsets.UTF_8)
    if (statusCode == 200 && html.contains("<title>Google</title>")) (statusCode, html.getBytes.length)
    else (statusCode, -1)
  }
}
