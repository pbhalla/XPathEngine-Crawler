Web Crawler

Web Crawler is used to browse the world wide web and its output can be used for creating inverted index and for implementing page rank algorithm. A web crawler starts with a set of URLs called Seed URLs. As and when it visits these URLs, it keeps on adding them to a list of visited URLs. In addition to that, it extracts the hyperlinks and adds them to a list of URLs, yet to be browsed. 

The given Web Crawler is a simple java application which accepts the following four command line arguments:

1.) The URL of the webpage at which to start, this is also called as the seed url. For plain HTTP URLs, we can open a socket to the port to send the request, whereas for HTTPS URLs we can use java.net.ssl.HttpsURLConnection.
2.) The directory containing the BerkleyDB database environment, which will hold the webpages that were downloaded by the crawler.We create the directory if it does not exist already.
3.) The maximum size of the document to be retrieved from the Web Server.
4.) An optional argument, indicating the number of files to be retrieved before the crawling stops.

The crawler can be run periodically by hand or it can run from an automated system. The crawler traverses links in HTML documents which can be extracted using an HTML parser like JTidy.It retrieves both the HTML and XML documents, and extracts the links that the given HTML page points to. When a new page is being processed it displays a short status like: "http://xyz.com/abc.html: Downloading". It will always send a HEAD request to check if the file has been modified since the last crawl, and only then will the file be downloaded.

Politeness Check:

The crawler, respects the robots.txt and it supports the Crawl Delay(Time between two crawls) directive. In addition to that it also checks for the disallowed and allowed links. 

