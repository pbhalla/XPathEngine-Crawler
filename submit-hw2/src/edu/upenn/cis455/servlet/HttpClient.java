package edu.upenn.cis455.servlet;

import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.io.*;

//import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;


public class HttpClient 
{

	static boolean proper_url=true;
	URL url_obj=null;
	String given_url=null;
	String host;
	int port;
	String given_path=null;
	String content_type=null;

	public HttpClient(String url)
	{
		try
		{
			url_obj=new URL(url);			
		}
		catch(MalformedURLException e)
		{
			System.out.println("Exception occurred malformed url....."+e);
			HttpClient.proper_url=false;

		}
		try
		{
			// extract all the necessary information required to send the request
			if(HttpClient.proper_url)
			{	
				given_url=url;
				host = url_obj.getHost();
				port = url_obj.getPort();
				if(port==-1)
					port=80;
				given_path=url_obj.getPath();
				// if no path

				if(given_path=="")
				{
					given_path="/";
				}	

			}
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred....."+e);
		}
	}

	public String obtainContent(String url)
	{
		StringBuffer getRequest=new StringBuffer();
		Socket soc;
		PrintWriter obj;
		StringBuffer content_read=new StringBuffer();
		try
		{
			if(HttpClient.proper_url)
			{
			  // form a get request
				soc=new Socket(host,port);
			    	
				obj=new PrintWriter(soc.getOutputStream(),true);
				obj.write("GET "+given_path+" HTTP/1.1\r\n");
				obj.write("UserAgent: cis455Servlet\r\n");
				obj.write("Host: "+host+":"+port+"\r\n");
				obj.write("Connection: close\r\n\r\n");
				//System.out.println(""getRequest.toString());
				//obj.write(getRequest.toString());
				obj.flush();
				//System.out.println("Request sent....");
			}	
			else
			 return null;	
			
			// read the contents of the response received
			InputStreamReader isr=new InputStreamReader(soc.getInputStream());
			
			BufferedReader br=new BufferedReader(isr);
			String nextLine=br.readLine();
			System.out.println(nextLine);
			if(nextLine.endsWith("200 OK"))
			{
				// check header
				
			  nextLine=br.readLine();
			  System.out.println(nextLine);
			  while( (nextLine != null) && !(nextLine.equals("")) )
			  {
				  // find the content-type
				  String type=nextLine.split(":")[0].trim();
				  if(type.equalsIgnoreCase("content-type"))
				  {
					 content_type=nextLine.split(":")[1].trim();
					 System.out.println("content type......"+content_type);
				  } 
				  nextLine=br.readLine();
				  System.out.println(nextLine);
				  System.out.println("In first while");
			  }	
			  
			  // read body
			  
			  // read the empty line after the header
			  
			  nextLine=br.readLine();
			  //nextLine = br.readLine();
			  System.out.println("Next line here...."+nextLine);
			  while(nextLine != null)
			  {
				  System.out.println("reading body...");
				  content_read.append(nextLine+"\r\n");
				  nextLine=br.readLine();
			  }	  
			  
			}	
			else
			{
				return null;
			}	
			
			br.close();
			obj.close();
			soc.close();
			
		}
		catch(Exception e)
		{
           System.out.println("Exception occurred while obtaining content from url...."+e);
		}
		System.out.println("Content Body......."+content_read.toString());
		return content_read.toString();
	}
	
	public Document obtainDomObject(String url)
	{
		// obtain the body of the given url
		String content_body=obtainContent(url);
		
		Document doc=null;
		// if the body is null return
		if(content_body != null)
		{
			
			// for html files
			System.out.println("conten type + " + content_type);
			
			if(content_type.endsWith("html"))
			{
				Tidy tidy=new Tidy();
				StringWriter writer=new StringWriter(content_body.length());
                StringReader reader=new StringReader(content_body);
				doc=tidy.parseDOM(reader,writer);
				return doc;
			}	
			
			//for xml files
			
			if(content_type.endsWith("xml"))
			{
				try
				{
					System.out.println("creating dom obj");
					doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(url_obj.openStream());
		       		return doc;
				}
				catch(Exception e)
				{
					System.out.println("Exception occurred while creating dom for xml...."+e);
				}
			}	
		}	
		else
		 return null;	
		
		return doc;
	}
	
	

}
