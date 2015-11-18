package edu.upenn.cis455.crawler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.upenn.cis455.servlet.HttpClient;
import edu.upenn.cis455.crawler.info.RobotsTxtInfo;
public class PolitenessCheck 
{
	HashMap<String,Long> last_accessed_time;
	String host;
	RobotsTxtInfo robots;
	//ArrayList<String> accessed_domains;

	public PolitenessCheck()
	{
		last_accessed_time=new HashMap<String,Long>();
		//accessed_domains= new ArrayList<String>();
		robots=new RobotsTxtInfo();
	}

	public boolean checkPolite(String url) throws MalformedURLException
	{
		URL url_obj=null;

		try
		{
			url_obj= new URL(url);
			host=url_obj.getHost();
		}
		catch(MalformedURLException e)
		{
			System.out.println("Exception occurred while extracting host from url..."+e);
		}
		if(!last_accessed_time.containsKey(host))
		{

			//download the robots.txt for that domain
			HttpClient client=new HttpClient(url);
			//System.out.println(url);
			String robots_url= url_obj.getProtocol()+"://"+url_obj.getHost()+"/robots.txt";
			//System.out.println("robots.txt url..."+robots_url);
			String content=client.obtainContent(robots_url);
			// checking if there is content in robots.txt

			if(content==null)
			{
				//System.out.println("Robots.txt not found");
				return true;
			}  
			else
			{
				// parsing robots.txt
				parseFile(content); 
				//System.out.println("after parsing robots.txt");
				// set the time when the domain was last accessed
				last_accessed_time.put(host,System.currentTimeMillis());
			}   

		}
		//checking if we can crawl the given domain again on basis of crawl_delay
		//System.out.println("checking for crawled delays...");

		Long crawl_delay=robots.getCrawlDelay(host);

		//System.out.println("Crawl_delay....."+crawl_delay);

		Long last_access=(long)0;
		if(last_accessed_time.containsKey(host))
		{	
			last_access=last_accessed_time.get(host);
		}  


		if(!((System.currentTimeMillis()-last_access)>crawl_delay*1000))
		{
			//System.out.println("Inside checking crawl delay....");
			try
			{
				Thread.sleep(System.currentTimeMillis()-last_access);
			}
			catch(Exception e)
			{
				System.out.println("Exception occurred..."+e);
			}
		}

		// check if the given url is banned or not

		// first check the list of allowed links
		//System.out.println("Checking for allowed links...");
		URL urlObj = new URL(url);
		if(robots.allowedContainAgent(host))
		{
			ArrayList<String> allowedLinks=robots.getAllowedLinks(host);
			Iterator it=allowedLinks.iterator();
			while(it.hasNext())
			{
				String allowdLink=(String)it.next();
				if(allowdLink.equals("/"))
					return false;
				String match = urlObj.getPath();
				
				if(match.startsWith(allowdLink)) {
					if(match.length() == allowdLink.length())
						return false;
					else if(match.charAt(allowdLink.length()) == '/')
						return false;
				}
			}   

		}

		// first check the list of allowed links
		//System.out.println("Checking for disallowed links...");
		if(robots.disallowedContainAgent(host))
		{
			ArrayList<String> disallowedLinks=robots.getDisallowedLinks(host);
			Iterator it=disallowedLinks.iterator();
			while(it.hasNext())
			{
				String disallowdLink=(String)it.next();
				if(disallowdLink.equals("/"))
					return false;
				String match = urlObj.getPath();
				
				if(match.startsWith(disallowdLink)) {
					if(match.length() == disallowdLink.length())
						return false;
					else if(match.charAt(disallowdLink.length()-1) == '/')
						return false;
				}
					
			}   

		}
		//System.out.println("Is polite.....");
		return true;

	}
	/**
	 * parsing robots.txt and setting the allowed/disallowed urls as well as the crawl delay for the given domain
	 * @param content
	 */

	public void parseFile(String content)
	{
		String user_agent="";
		String disallowed_url="";
		String allowed_url="";
		long crawl_delay=0;
		//parsing the robots.txt
		//System.out.println(content);
		Matcher matcher=Pattern.compile("(\\S+)(:[ ]?)(\\S+)([ ]?)").matcher(content);
		while(matcher.find())
		{
			//splitting each line on the basis of :
			//String values[]=each_line.split(":",1);
			/*
			System.out.println(values[0].trim());
			System.out.println(values[1].trim());
			 */
			String value1 = matcher.group(1);
			String value2= matcher.group(3);
			// System.out.println(value1);
			//System.out.println(value2);

			if(value1.equalsIgnoreCase("user-agent"))
			{
				user_agent=value2;
				// check if the user_agent is cis455Crawler
				if(user_agent.equals("cis455crawler"))
				{
					robots.clearDisallowedLinks(host);
					robots.clearDisallowedLinks(host);
				}		

			}

			// check for allowed urls
			else if(value1.equalsIgnoreCase("allow"))
			{
				if((user_agent.equals("cis455crawler"))||(user_agent.equals("*")))
				{
					allowed_url=value2;
					robots.addAllowedLink(host,allowed_url);
				}	
			}	

			// check for disallowed urls
			else if(value1.equalsIgnoreCase("disallow"))
			{
				if((user_agent.equals("cis455crawler"))||(user_agent.equals("*")))
				{
					disallowed_url=value2;
					robots.addDisallowedLink(host,disallowed_url);
				}	
			}	

			// check for crawl-delay
			else if(value1.equalsIgnoreCase("crawl-delay"))
			{
				if((user_agent.equals("cis455crawler"))||(user_agent.equals("*")))
				{
					crawl_delay=Long.parseLong(value2);
					robots.addCrawlDelay(host, crawl_delay);
				}					
			}	

		}



	}

}
