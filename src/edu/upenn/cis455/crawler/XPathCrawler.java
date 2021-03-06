package edu.upenn.cis455.crawler;

import java.util.ArrayList;
import java.util.LinkedList;

import edu.upenn.cis455.storage.DBWrapper;

public class XPathCrawler //extends Thread 
{
	static String seed_url_webpage;
	static String dbDir;
	static int document_size;
	static int no_of_files;
	ArrayList<Thread> threadPool;
	//creating linked list to store the urls 
	public static LinkedList<String> shared_url_queue;
	/**
	 * default constructor
	 */
	public XPathCrawler()
	{

	}
	/**
	 * the constructor of the XPathCrawler class sets the 4 parameters
	 * @param url_webpage
	 * @param dbDir
	 * @param document_size
	 * @param no_of_files
	 */
	public XPathCrawler(String url_webpage, String dbDir, int document_size, int no_of_files )
	{
		this.seed_url_webpage=url_webpage;
		this.dbDir=dbDir;
		this.document_size=document_size;
		this.no_of_files=no_of_files;
		new DBWrapper(dbDir);
		shared_url_queue=new LinkedList<String>();
		/*
		threadPool=new ArrayList<Thread>();
		
		// creating thread pool with a fixed size 
		for(int i=1;i<=10;i++)
		{
			try
			{
				CrawlerThread obj=new CrawlerThread();
				Thread t=new Thread(obj);
				t.setName("Thread "+i);
				threadPool.add(t);
				t.start();

			}
			catch(Exception e)
			{
				System.out.println("Exception while creating a thread pool"+e);
				//e.printStackTrace();
			}
		}		

           */
	}
	/**
	 * run function of XPathCrawler to add the seed url to the queue....
	 */

	public void run()
	{
		try
		{
			UrlQueue queue_obj=new UrlQueue();
			queue_obj.addUrl(seed_url_webpage);

		}
		catch(Exception e)
		{
			System.out.println("Exception occurred while adding seed url to queue....."+e);
		}
		
		CrawlerThread obj=new CrawlerThread();
		obj.run();
	}

	public void shutdown_crawler()
	{

	}

	/**
	 * main function to initialize parameters to start crawler thread
	 * @param args
	 */
	public static void main(String args[])
	{
		String url=null;
		String dbDir=null;
		int file_size=0;
		int no_of_files=0;

		if(args.length<3)
		{
			System.out.println("Improper call...minimum 3 parameters required..");
		}		
		else if(args.length==3)
		{
			url=args[0];
			dbDir=args[1];
			file_size=Integer.parseInt(args[2]);
			no_of_files=200;
		}	
		else if(args.length==4)
		{
			url=args[0];
			dbDir=args[1];
			file_size=Integer.parseInt(args[2]);
			no_of_files=Integer.parseInt(args[3]);


		}	

		XPathCrawler crawler=new XPathCrawler(url,dbDir,file_size,no_of_files);
		/*
		try
		{
			Thread t=new Thread(crawler);
			crawler.start();
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred while creating xPath crawler thread..."+e);
		}
		*/
		crawler.run();

	}


}
