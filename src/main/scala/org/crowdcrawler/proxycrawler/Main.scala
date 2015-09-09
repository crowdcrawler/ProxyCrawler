package org.crowdcrawler.proxycrawler

import java.nio.charset.StandardCharsets
import java.nio.file.{Paths, Files}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.typesafe.scalalogging.Logger
import org.crowdcrawler.proxycrawler.checker.ProxyChecker
import org.slf4j.LoggerFactory


object Main {
  private val LOGGER = Logger(LoggerFactory.getLogger(Main.getClass))
  val OBJECT_MAPPER = new ObjectMapper() with ScalaObjectMapper
  OBJECT_MAPPER.registerModule(DefaultScalaModule)

  def main(args: Array[String]): Unit = {
    val usage = "Usage: \n\tcrawl [pluginClassName]* OutputFile\n" +
      "\tcheck proxies.json valid_proxies.json\n" +
      "\tfilter valid_proxies.json <HTTP|HTTPS|SOCKS> output.json\n" +
      "For example:\n" +
      "\t1. Crawl all supported websites and save proxies to proxies.json\n" +
      "\t\tcrawl proxies.json\n" +
      "\t2. Crawl www.cnproxy.com and save proxies to proxies.json:\n" +
      "\t\tcrawl CnProxyComPlugin proxies.json\n" +
      "\t3. Check the speed of proxies.\n" +
      "\t\tcheck proxies.json valid_proxies.json\n" +
      "\t4. Filter proxies by schema\n" +
      "\t\tfilter valid_proxies.json HTTP http.json\n"
    if (args.length < 2) {
      println(usage)
      return
    }

    val start = System.currentTimeMillis
    if (args(0) == "crawl") {
      val classNames = if (args.length == 2) {
        Array("CnProxyComPlugin", "CoolProxyNetPlugin", "GatherproxyComPlugin", "IpcnOrgPlugin",
          "ProxyListOrg", "SocksProxyNet", "USProxyOrgPlugin")
      } else {
        args.slice(1, args.length-1)
      }
      val crawler = ProxyCrawler(classNames: _*)

      val proxies = crawler.crawl()

      LOGGER.info("Writing to disk, " + proxies.size + " proxies")
      val json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter.writeValueAsString(proxies)
      Files.write(Paths.get(args.last), json.getBytes(StandardCharsets.UTF_8))
    } else if (args(0) == "check") {
      val json = io.Source.fromFile(args(1), "utf-8").mkString
      val list = OBJECT_MAPPER.readValue[List[ProxyInfo]](json)

      // sort by speed desc
      val validProxies = ProxyChecker.check(list).filter(_.speed > 0)
        .sortWith((p1, p2) => p1.speed > p2.speed)
      LOGGER.info("Writing to disk, " + validProxies.size + " valid proxies out of " + list.size + " proxies")
      val newJson = OBJECT_MAPPER.writerWithDefaultPrettyPrinter
        .writeValueAsString(validProxies)
      Files.write(Paths.get(args(2)), newJson.getBytes(StandardCharsets.UTF_8))

    } else if (args(0) == "filter") {
      val json = io.Source.fromFile(args(1), "utf-8").mkString
      val list = OBJECT_MAPPER.readValue[List[ProxyInfo]](json)
      val filtered = if (args(2) == "SOCKS") {
        list.filter(p => p.schema == "SOCKS" | p.schema == "SOCKS4" || p.schema == "SOCKS5")
      } else {
        list.filter(p => p.schema == args(2))
      }

      val newJson = OBJECT_MAPPER.writerWithDefaultPrettyPrinter
        .writeValueAsString(filtered)
      Files.write(Paths.get(args(3)), newJson.getBytes(StandardCharsets.UTF_8))
    } else {
      println(usage)
      return
    }

    val end = System.currentTimeMillis
    LOGGER.info("Time elapsed " + (end - start) / 1000 + " seconds.")
  }
}
