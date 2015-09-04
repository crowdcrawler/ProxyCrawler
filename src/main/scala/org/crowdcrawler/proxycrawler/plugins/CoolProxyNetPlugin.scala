package org.crowdcrawler.proxycrawler.plugins

import org.crowdcrawler.proxycrawler.ProxyInfo
import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.StandardCharsets
import sun.misc.BASE64Decoder
import scala.collection.mutable
import scala.collection.JavaConversions._
import util.control.Breaks._


/**
 * For <a href=http://www.cool-proxy.net>cool-proxy.net</a>
 */
class CoolProxyNetPlugin extends AbstractPlugin {
  private final val decoder: BASE64Decoder = new BASE64Decoder

  val seeds: List[URI] = List(new URI("http://www.cool-proxy.net/proxies/http_proxy_list/page:1"))

  private def decryptIP(ip: String): String = {
    val base64Encoded = new StringBuilder

    for (ch <- ip) {
      val newChar =
        if (Character.isAlphabetic(ch)) {
          if (ch.toLower < 'n') (ch + 13).toChar else (ch - 13).toChar
        } else {
          ch
        }
      base64Encoded += newChar
    }

    val bytes = decoder.decodeBuffer(base64Encoded.toString())
    new String(bytes, StandardCharsets.UTF_8)
  }

  def extract(html: String): List[ProxyInfo] = {
    val result = mutable.ListBuffer.empty[ProxyInfo]
    val doc = Jsoup.parse(html)
    val rows = doc.select("table > tbody > tr")
    for (row <- rows) {
      breakable {
        val tds = row.select("td")
        if (tds.isEmpty) break
        val host = {
          val hostTmp = tds.get(0).html
          val startWith = "Base64.decode(str_rot13(\""
          val start = hostTmp.indexOf(startWith)
          if (start == -1) break
          val end = hostTmp.indexOf("\")))", start)
          if (end == -1) break
          val hostEncrypted = hostTmp.substring(start + startWith.length, end)
          decryptIP(hostEncrypted)
        }
        val port = tds.get(1).text.toInt
        val location = tds.get(3).text
        val speed = tds.get(8).text.toInt
        result.add(ProxyInfo(host, port, "HTTP", speed, location, null))
      }
    }
    result.toList
  }

  def next(html: String): List[URI] = {
    val result = mutable.ListBuffer.empty[URI]
    val doc = Jsoup.parse(html)
    val rows = doc.select(".pagination > span > a[href]")
    for (row <- rows) {
      val href = row.attr("href")
      result += new URI("http://www.cool-proxy.net" + href)

    }
    result.toList
  }
}
