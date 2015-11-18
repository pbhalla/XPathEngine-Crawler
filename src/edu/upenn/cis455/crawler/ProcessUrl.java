package edu.upenn.cis455.crawler;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.upenn.cis455.xpathengine.XPathEngineImpl;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import edu.upenn.cis455.storage.*;
public class ProcessUrl
{
	CrawledUrlEntity crawled_url;
	UrlQueue queue_obj;
	String url;
	ArrayList<String> obtainedUrls;

	public ProcessUrl()
	{
		obtainedUrls=new ArrayList<String>();

	}
	/**
	 * process the given url by extracting all links
	 * @param page
	 * @param queue_obj
	 */
	public void processUrl(CrawledUrlEntity page, UrlQueue queue_obj)
	{
		// checking if crawledUrlEntity is null or not
		if(page==null)
		{
			//System.out.println("page is null....");
			return;
		}   
		// checking the content type of the page
		//System.out.println("Inside process_url");
		String type=page.getContentType();
		//System.err.println("[debug]in process url...."+type);
		boolean check_html= type.equals("text/html");
		if(check_html)
		{   
			obtainedUrls= new ArrayList<String>();  
			getAllLinks(page);  
		}
		// update the matched urls list corressponding to every channel
		else {
			check_html = false;
			//System.out.println("Inside update channels");
			updateChannels(page);
			
		}

		//remove duplicate urls
		if(check_html)
			remove_duplicate_url(queue_obj);  
		
		//System.out.println("Url processed...");

	}

	public void remove_duplicate_url(UrlQueue queue_obj)
	{


		Iterator it=obtainedUrls.iterator();
		while(it.hasNext())
		{
			String url_temp=(String)it.next();
			if(queue_obj.contains_url(url_temp))
				continue;
			else
				queue_obj.addUrl(url_temp);  
		}   


	}
	/**
	 * get all href links from the html page and convert them to absolute form 
	 * @param page
	 */
	public void getAllLinks(CrawledUrlEntity page)
	{
		url=page.getUrl();
		URL urlObject=null;
		try {
			urlObject = new URL(url);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String link_regex = "<a\\s+href\\s*=\\s*[\\'\\\"](.*?)[\\\"]";
		Matcher link_matcher =Pattern.compile(link_regex,Pattern.CASE_INSENSITIVE).matcher(page.getContent());
		while(link_matcher.find())
		{
			String link=link_matcher.group(1).trim();

			// checking for links which are not href
			if(link.equals("#") || link.toLowerCase().startsWith("mailto") || link.toLowerCase().startsWith("javascript"))
				continue;
			if(link.startsWith("http"))
			{
				obtainedUrls.add(link);
				continue;
			}
			/*
			// checking for relative links
			if(link.startsWith("/"))
			{
				String temp_path=urlObject.getPath();
				if(temp_path.endsWith("html")||temp_path.endsWith("htm"))
				{	
					String path = urlObject.getPath().substring(0, urlObject.getPath().lastIndexOf('/'));
					link=urlObject.getProtocol()+"://"+urlObject.getHost()+path+link;
				}
			}
			else
			{
				String path = urlObject.getPath().substring(0, urlObject.getPath().lastIndexOf('/'));
				link=urlObject.getProtocol()+"://"+urlObject.getHost()+path+"/"+link; 
			} */
			String absPath = urlObject.getPath();
			if(absPath.endsWith("html") || absPath.endsWith("htm"))
				absPath = absPath.substring(0,absPath.lastIndexOf("/"));

			if (link.startsWith("/")) 
			{
				if(! absPath.endsWith("/"))
					link = urlObject.getProtocol() + "://" + urlObject.getHost() + absPath + link;
				else 
					link = urlObject.getProtocol() + "://" + urlObject.getHost() + absPath.substring(0, absPath.length()-1) + link;
			}

			else { 
				if(! absPath.endsWith("/"))
					link = urlObject.getProtocol() + "://" + urlObject.getHost() + absPath + "/" + link;
				else 
					link = urlObject.getProtocol() + "://" + urlObject.getHost() + absPath + link;
			}

			// System.out.println("[Output from log4j] Site link to add +" + site_link);



			obtainedUrls.add(link);
		}	
	}

	public void updateChannels(CrawledUrlEntity url_page )
	{
		//System.err.println("[debug] in updateChannels");
		// get the document object
		Document dom=get_dom_for_xml(url_page.getContent());
		//System.err.println("[debug] is dom null ? + " + dom==null);
         url=url_page.getUrl();
		// obtain all the channels

		ArrayList<ChannelEntityClass> all_channels= DBWrapper.getAllChannels();
		Iterator it=all_channels.iterator();
		while(it.hasNext())
		{
			ChannelEntityClass channel_obj=(ChannelEntityClass)it.next();
			System.err.println("[debug] is channel ? + " + channel_obj.getChannelName());
			ArrayList<String> xPaths=channel_obj.getXPaths();
			//String[] xpath_array=(String[])xPaths.toArray();
			String[] xpath_array = xPaths.toArray(new String[xPaths.size()]);
			XPathEngineImpl obj=new XPathEngineImpl();
			obj.setXPaths(xpath_array);
			if(dom!=null)
			{
				System.err.println("[debug] before evaluate");
				boolean[] result = obj.evaluate(dom);
				//System.out.println("After evaluate");
				for(int i = 0; i < result.length; i ++)
				{
					if(result[i])
					{
						System.err.println("[debug] result matched");
						channel_obj.addMatchedURL(url);
						DBWrapper.putChannel(channel_obj);
					}	
				}		
			}	
		}   

	}
	public Document get_dom_for_xml(String content)
	{
		Document doc=null;
		try
		{
			//System.out.println("creating dom obj");
			String encode="UTF-8";
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(content.getBytes(encode)));
			return doc;
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred while creating dom for xml...."+e);
		}
		return doc;
	}
}
