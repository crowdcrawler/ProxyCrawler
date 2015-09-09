name := "proxy-crawler"
version := "1.1"
organization := "org.crowdcrawler"
description := "Crawling Free HTTP and SOCKS Proxies on Internet"
organizationHomepage := Some(new URL("http://www.crowdcrawler.org"))
homepage := Some(new URL("https://github.com/crowdcrawler/proxy-crawler"))
startYear := Some(2015)
licenses := Seq("Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0"))
scalaVersion := "2.11.7"
javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
scalacOptions ++= Seq("-feature", "-language:_", "-unchecked", "-deprecation", "-encoding", "utf8")

// Use local repositories by default
resolvers ++= Seq(
  Resolver.defaultLocal,
  Resolver.mavenLocal
)


// sbt-assembly settings for building a fat jar
// Slightly cleaner jar name
assemblyJarName in assembly := name.value + "-" + version.value + ".jar"
mainClass in assembly := Some("org.crowdcrawler.proxycrawler.ProxyCrawler")


libraryDependencies ++= {
  val jsoupVersion = "1.8.3"
  val httpClientVersion = "4.5"
  val jacksonVersion      = "2.6.1"
  val scalaTestVersion    = "3.0.0-SNAP5"
  val akkaVersion       = "2.4.0-RC2"
  Seq(
    "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
    "org.jsoup" % "jsoup" % jsoupVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "org.slf4j" % "slf4j-log4j12" % "1.7.12" % "runtime",
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
    "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % jacksonVersion,
    "org.apache.httpcomponents" % "httpclient" % httpClientVersion,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  )
}
