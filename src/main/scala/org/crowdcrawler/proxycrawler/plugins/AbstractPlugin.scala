package org.crowdcrawler.proxycrawler.plugins

import java.net.URI
import java.nio.charset.{StandardCharsets, Charset}

import org.crowdcrawler.proxycrawler.ProxyCrawler
import org.crowdcrawler.proxycrawler.ProxyInfo

/**
 * One plugin per website.
 */
trait AbstractPlugin {
  /**
   * Seed URIs to start with.
   * @return A list of URI without null elements.
   */
  def seeds: List[URI]

  /**
   * Extract proxies from the html source.
   * @param html html source
   * @return A list of ProxyInfo without null elements, empty list when no proxies.
   */
  def extract(html: String): List[ProxyInfo]

  /**
   * Extract URLs from the html source.
   *
   * The {@link ProxyCrawler} will stop crawling even this method return an non-empty list,
   * if all of the URIs are already existed the returned list will be viewed as empty.
   *
   * But still, try not to return duplicated URIs.
   * @param html
   * @return A list of URI without null elements, empty list when no next URIs.
   */
  def next(html: String): List[URI]

  /**
   * Customized DEFAULT_HEADERS, can be overrided by users.
   *
   * If the default DEFAULT_HEADERS are not legal to the target website,
   * then you need to customize your own DEFAULT_HEADERS.
   */
  def customizedHeaders: Map[String, String] = ProxyCrawler.DEFAULT_HEADERS

  /**
   * The charset of http response.
   *
   * If the website is not using UTF8, then you need to override this method.
   */
  def responseCharset: Charset = StandardCharsets.UTF_8
}
