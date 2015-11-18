package edu.upenn.cis455.crawler;


public class UrlQueue 
{
	public void addUrl(String url) 
	{
		try
		{
			//logger.info("adding request to the requestQueue");
			synchronized(XPathCrawler.shared_url_queue)
			{
				XPathCrawler.shared_url_queue.add(url);
				XPathCrawler.shared_url_queue.notifyAll();
			}
		}
		catch (Exception e)
		{
			System.out.println("Exception occurred while adding the url to the queue..."+e);
		}
	}
	/**
	 * removes the head url element in the shared_url_queue  
	 * @return
	 */
	public String removeUrl() 
	{
		try
		{
			//if there is no request then the thread should wait
			synchronized(XPathCrawler.shared_url_queue)
			{
				while(XPathCrawler.shared_url_queue.isEmpty())

				{
					//System.out.println("url queue is currently empty");
					XPathCrawler.shared_url_queue.wait();
				}	
				//if there is a url in shared_url_queue remove that request and process it.
				XPathCrawler.shared_url_queue.notifyAll();
				//poll() function to remove the head element in the list
				return XPathCrawler.shared_url_queue.poll();
			}
		}
		catch (Exception e) 
		{
			return null;
		}

	}

	public boolean contains_url(String url)
	{
		synchronized(XPathCrawler.shared_url_queue)
		{
			return XPathCrawler.shared_url_queue.contains(url);
		}
	}

}
