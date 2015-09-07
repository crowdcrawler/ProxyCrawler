package org.crowdcrawler.proxycrawler.checker

import java.net.URI
import java.nio.charset.StandardCharsets

import org.apache.http.annotation.ThreadSafe
import org.apache.http.HttpHost
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils


@ThreadSafe
object HttpProxyChecker extends AbstractProxyChecker {
  private val CLIENT  = HttpClients.custom().setMaxConnTotal(AbstractProxyChecker.MAX_CONN)
    .disableRedirectHandling().build()
  private val TARGET_URL = new URI("http://www.baidu.com")


  def check(host: String, port: Int): (Int, Int) = {
    val request = new HttpGet(TARGET_URL)
    AbstractProxyChecker.configureRequest(request, Some(new HttpHost(host, port, "http")))

    val response = CLIENT.execute(request)

    val statusCode = response.getStatusLine.getStatusCode
    val html = EntityUtils.toString(response.getEntity, StandardCharsets.UTF_8)
    if (statusCode == 200 && html.contains("<title>百度一下")) (statusCode, html.getBytes.length) else (statusCode, -1)
  }
}
