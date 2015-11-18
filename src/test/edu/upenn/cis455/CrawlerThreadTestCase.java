/**
 * 
 */
package test.edu.upenn.cis455;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.LinkedList;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import edu.upenn.cis455.crawler.CrawlerThread;
import edu.upenn.cis455.crawler.ProcessUrl;
import edu.upenn.cis455.crawler.UrlQueue;
import edu.upenn.cis455.crawler.XPathCrawler;
import edu.upenn.cis455.storage.ChannelEntityClass;
import edu.upenn.cis455.storage.CrawledUrlEntity;
import edu.upenn.cis455.storage.DBWrapper;

/**
 * @author cis455
 *
 */
public class CrawlerThreadTestCase extends TestCase
{

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception 
	{
		
	}

	/**
	 * Test method for {@link edu.upenn.cis455.crawler.CrawlerThread#run()}.
	 */
	@Test
	public void testRun() 
	{
		DBWrapper db_ob=new DBWrapper("/home/cis455/database");
		String channel_name="weatherChannel";
		ArrayList<String> xPaths= new ArrayList<String>();
		xPaths.add("/dwml/head/product");
		
		ChannelEntityClass channel =new ChannelEntityClass(channel_name,xPaths,"abc.xsl");
		
		DBWrapper.putChannel(channel);
		
		
		String url = "https://dbappserv.cis.upenn.edu/crawltest/misc/weather.xml";
		XPathCrawler xpc = new XPathCrawler();
		String [] args = {url, "/home/cis455/database","100","10"};
		xpc.main(args);
		// add channel
		//seed_url_webpage=url_webpage;
		//this.dbDir=dbDir;
		//document_size=document_size;
		//this.no_of_files=no_of_files;
		//new DBWrapper(dbDir);
		//shared_url_queue=new LinkedList<String>();
		
		//CrawlerThread obj=new CrawlerThread();
		//ProcessUrl processUrl=new ProcessUrl();
		//UrlQueue queue=new UrlQueue();
		
		//crawling
		try
		{
			/*
			XPathCrawler.shared_url_queue.add(url);
			obj.run();
			*/
		  //CrawledUrlEntity page = obj.fetchWebpage(url);
		  //processUrl.processUrl(page, queue);
		 
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred..."+e);
		}
		
		ChannelEntityClass channel_test=DBWrapper.getChannel(channel_name);
		ArrayList<String> matchedUrls=channel_test.getMatchedUrls();
		
		assertEquals(url,matchedUrls.get(0));
		
		//fail("Not yet implemented");
	}
	
	@Test
	public void testRun1() 
	{
		DBWrapper db_ob=new DBWrapper("/home/cis455/database1");
		
		
		String url = "https://dbappserv.cis.upenn.edu/crawltest/marie/private/middleeast.xml";
		XPathCrawler xpc = new XPathCrawler();
		String [] args = {url, "/home/cis455/database","100","10"};
		xpc.main(args);
		// add channel
		//seed_url_webpage=url_webpage;
		//this.dbDir=dbDir;
		//document_size=document_size;
		//this.no_of_files=no_of_files;
		//new DBWrapper(dbDir);
		//shared_url_queue=new LinkedList<String>();
		
		//CrawlerThread obj=new CrawlerThread();
		//ProcessUrl processUrl=new ProcessUrl();
		//UrlQueue queue=new UrlQueue();
		
		//crawling
		try
		{
			/*
			XPathCrawler.shared_url_queue.add(url);
			obj.run();
			*/
		  //CrawledUrlEntity page = obj.fetchWebpage(url);
		  //processUrl.processUrl(page, queue);
		 
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred..."+e);
		}
		
	    CrawledUrlEntity obj=DBWrapper.getCrawledUrlEntity(url);
		
		
		assertEquals(null,obj);
		
		//fail("Not yet implemented");
	}
	
	

}
