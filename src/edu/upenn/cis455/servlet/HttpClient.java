package edu.upenn.cis455.servlet;

import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.io.*;

import javax.net.ssl.HttpsURLConnection;
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
		set_parameters_for_url(url);
	}
	/*
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
	 */

	public String obtainContent(String url)
	{
		URL urlObj=null;
		try
		{
			urlObj=new URL(url);
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred before https in obtain content.."+e);
		}
		String protocol=urlObj.getProtocol();
		StringBuffer content_read=new StringBuffer("");
		if(protocol.equalsIgnoreCase("https"))
		{
			try
			{
				int status_code=0;
				HttpsURLConnection con = null;

				con = (HttpsURLConnection)urlObj.openConnection();
				con.setRequestMethod("GET");
				con.setRequestProperty("User-Agent", "cis455crawler");
				con.setInstanceFollowRedirects(false);
				con.setRequestProperty("Host" ,host + ":" + port );
				status_code = con.getResponseCode();

				if (status_code!=200 && status_code!=301 && status_code!=304)
					return null;
				this.content_type = con.getContentType();

				BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String thisLine;


				while((thisLine = br.readLine()) != null)
				{
					//System.out.println("Inside obta");
					content_read.append(thisLine+"\n");
				}
				br.close();
			}
			catch(Exception e)
			{
				System.out.println("Exception occurred in https connection...."+e);
			}

			//return content;
		}
		else
		{	

			StringBuffer getRequest=new StringBuffer();
			Socket soc;
			PrintWriter obj;
			//StringBuffer content_read=new StringBuffer();
			try
			{
				if(HttpClient.proper_url)
				{
					// form a get request
					soc=new Socket(host,port);

					obj=new PrintWriter(soc.getOutputStream(),true);
					obj.write("GET "+given_path+" HTTP/1.1\r\n");
					obj.write("UserAgent: cis455crawler\r\n");
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
				//System.out.println(nextLine);
				if(nextLine.endsWith("200 OK"))
				{
					// check header

					nextLine=br.readLine();
					//System.out.println(nextLine);
					while( (nextLine != null) && !(nextLine.equals("")) )
					{
						// find the content-type
						String type=nextLine.split(":")[0].trim();
						if(type.equalsIgnoreCase("content-type"))
						{
							content_type=nextLine.split(":")[1].trim();
							//System.out.println("content type......"+content_type);
						} 
						nextLine=br.readLine();
						//System.out.println(nextLine);
						//System.out.println("In first while");
					}	

					// read body

					// read the empty line after the header

					nextLine=br.readLine();
					//nextLine = br.readLine();
					//System.out.println("Next line here...."+nextLine);
					while(nextLine != null)
					{
						//System.out.println("reading body...");
						content_read.append(nextLine+"\n");
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
			//System.out.println("Content Body......."+content_read.toString());
		}
		return content_read.toString();
	}

	public String getUrl()
	{
		return given_url;
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
			//System.out.println("conten type + " + content_type);

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
					//System.out.println("creating dom obj");
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

	public boolean set_parameters_for_url(String url)
	{

		try
		{
			url_obj=new URL(url);			
		}
		catch(MalformedURLException e)
		{
			System.out.println("Exception occurred malformed url....."+e);
			proper_url=false;

		}
		try
		{
			// extract all the necessary information required to send the request
			if(proper_url)
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
		return proper_url;
	}


	public HashMap<String,String> getHead(String url)
	{
		//System.out.println("Inside get head....");
        URL urlObj=null;
		boolean set_parameters= set_parameters_for_url(url);
		
		HashMap<String,String> response_header_content=null;
		if(!set_parameters)
			return null;
		try
		{
		  urlObj=new URL(url);
		}
		catch(Exception e)
		{
			System.out.println("Exception before https connection...."+e);
		}
		String protocol= urlObj.getProtocol();
		//System.out.println("Inside get head...."+protocol);
		if(protocol.equalsIgnoreCase("https"))
		{
			//System.out.println("Inside get head https...");
			try
			{
				int status_code=0;
				HttpsURLConnection con = null;

				con = (HttpsURLConnection)urlObj.openConnection();
				//con = (HttpsURLConnection) this.url_obj.openConnection();
				con.setRequestMethod("GET");
				con.setRequestProperty("User-Agent", "cis455crawler");
				con.setInstanceFollowRedirects(false);
				con.setRequestProperty("Host" , host + ":" + port);
				status_code = con.getResponseCode();
				
				if (status_code!=200 && status_code!=301 && status_code!=304)
					return null;
				response_header_content = new HashMap<String, String>();
				response_header_content.put("content-type", ""+con.getContentType());
				response_header_content.put("content-length",""+con.getContentLength());
				response_header_content.put("last-modified",""+getProperDate(con.getLastModified()));
				if(con.getHeaderField("Location")!=null)
				{
					//System.out.println("Inside relocation...");
				 response_header_content.put("location", con.getHeaderField("Location"));
				}	 
				return response_header_content;


			}
			catch(Exception e)
			{
				System.out.println("Exception occurred in https connection...."+e);
			}

			//return content;
		}

		else
		{	
			try
			{
				Socket soc=new Socket(host,port);
				// creating a head request
				PrintWriter obj=new PrintWriter(soc.getOutputStream(),true);
				obj=new PrintWriter(soc.getOutputStream(),true);
				obj.write("HEAD "+given_path+" HTTP/1.1\r\n");
				obj.write("UserAgent: cis455crawler\r\n");
				obj.write("Host: "+host+":"+port+"\r\n");
				obj.write("Connection: close\r\n\r\n");
				obj.flush();

				// read head response
				BufferedReader br=new BufferedReader(new InputStreamReader(soc.getInputStream()));
				response_header_content=new HashMap<String,String>();
				String nextLine=br.readLine();
				//check for status codes
				/*if(nextLine.contains("301") || nextLine.contains("302"))
				{
					String new_url= get_new_url(br);
					return getHead(new_url);
				}*/
				if(!nextLine.endsWith("200 OK") || !nextLine.contains("301") || !nextLine.contains("302"))
					return null;
				nextLine=br.readLine();
				while(nextLine !=null && !nextLine.equals(""))
				{
					String header=nextLine.split(":")[0].trim();
					String value=nextLine.split(":")[1].trim();
					response_header_content.put(header.toLowerCase(), value);
					nextLine=br.readLine();
				}
				obj.close();
				br.close();
				soc.close();
			}
			catch(Exception e)
			{
				System.out.println("Exception occurred while sending the head request..."+e);
			}
		}
		return response_header_content;

	}

	public String get_new_url(BufferedReader br)
	{
		// get new url
		try
		{
			String nextLine=br.readLine();
			while(nextLine!=null && !nextLine.equals(""))
			{
				// check for location
				if(nextLine.contains(":"))
				{
					String header=nextLine.split(":")[0].trim();

					if(header.equalsIgnoreCase("Location"))
					{
						int len=header.length();
						String new_url=nextLine.substring(len+1,nextLine.length());
						return new_url;
					} 
				}
				nextLine=br.readLine();
			}	

		}
		catch(Exception e)
		{
			System.out.println("Exception occurred while obtaining the redirect url..."+e);
		}
		return null;
	}
	
	private String getProperDate(long time)
	{
		Date date = new Date(time);
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		String ret_date = sdf.format(date).toString();
		return ret_date;
	}

}
