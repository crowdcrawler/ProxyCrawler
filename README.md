# ProxyCrawler [![Build Status](https://travis-ci.org/crowdcrawler/ProxyCrawler.png)](https://travis-ci.org/crowdcrawler/ProxyCrawler)
Crawl Free HTTP and SOCKS Proxies on Internet


## Compile

    sbt clean assembly

## Usage

    crawl [pluginClassName]* OutputFile
    check proxies.json valid_proxies.json
    
For example:

1. Crawl all supported websites and save proxies to proxies.json

        crawl proxies.json
        
2. Crawl www.cnproxy.com and save proxies to proxies.json:

		crawl CnProxyComPlugin proxies.json
		
3. Check the speed of proxies.

		check proxies.json valid_proxies.json

## Load into IntelliJ Idea

Run `sbt gen-idea` to create Idea project files, and click `File->Open...` to open the project's root folder then you're all set.

Intellij Idea claims that it can import SBT projects directly, but sometimes it fails, so this is why you need to generate project files by using `sbt gen-idea`.

## Static Analyzer

### linter (https://github.com/HairyFotr/linter)

Usage: automatically runs during Compilation and evaluation in console

### sbt-scapegoat (https://github.com/sksamuel/sbt-scapegoat)

Usage: automatically runs during Compilation

Open target/scala-2.11/scapegoat.xml or target/scala-2.11/scapegoat.html

## Coding Style Checker

### ScalaStyle

Usage: ```sbt scalastyle```

Open `target/scalastyle-result.xml`

Check level are all "warn", change to "error" if you want to reject code changes when integrated with CI tools.