package edu.upenn.cis455.servlet;

import edu.upenn.cis455.xpathengine.XPathEngineImpl;

import javax.servlet.http.*;

import java.io.*;
import java.net.URLDecoder;

import javax.servlet.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import edu.upenn.cis455.xpathengine.XPathEngineImpl;

@SuppressWarnings("serial")
public class XPathServlet extends HttpServlet
{

	/* TODO: Implement user interface for XPath engine here */

	/* You may want to override one or both of the following methods */

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
	{
		/* TODO: Implement user interface for XPath engine here */
		/* TODO: Implement user interface for XPath engine here */
		String xPath=request.getParameter("xpath");
		String url=request.getParameter("url");
		String url_decode=null;
		// checking if either the xpath or url is null or empty
         System.out.println("Parameters received.........");
		String[] final_xPaths=null;
		if(xPath==null||url==null||xPath.trim().equals("")||url.trim().equals(""))
		{
			try
			{
				System.out.println("Redirected to same page.....");
				response.sendRedirect(request.getContextPath() + "/xpath");
				return;
			}
			catch(Exception e)
			{
				System.out.println("Exception occurred url is empty..."+e);
			}
		} 	 


		try
		{

			String[] indvidual_xPaths=xPath.split(";");
			final_xPaths=new String[indvidual_xPaths.length];
			for(int i=0;i<indvidual_xPaths.length;i++)
			{
			  String temp= URLDecoder.decode(indvidual_xPaths[i],"UTF-8");
			  final_xPaths[i]=temp.trim();
			  System.out.println("XPath........"+final_xPaths[i]);
			}	
			
		   url_decode=URLDecoder.decode(url,"UTF-8");
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred while decoding the url...."+e);
		}
		// checking for absolute url

		if(url.startsWith("http://")|| url.startsWith("HTTP://"))
		{
			url=url_decode;
		}	
		else
		{
			url="http://"+url_decode;
		}	
	
        System.out.println("URL obtained......."+url);
		HttpClient client=new HttpClient(url);
		// obtain DOM object
		Document dom=client.obtainDomObject(url);
		/*
		File xml_file = new File("/home/cis455/Downloads/courses.xml");
        Document dom = null;
        System.out.println("[Output from log4j] Creating DOM Object");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            dom = builder.parse(xml_file);
            System.out.println("[Output from log4j] Passed Creating DOM Object");
        }
        catch (ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (SAXException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
        //System.out.println("DOM object received......."+ dom);
		XPathEngineImpl obj=new XPathEngineImpl();
		//String[] xPath_array=new String[1];
		//xPath_array[0]=xPath;
		obj.setXPaths(final_xPaths);
		try
		{
			PrintWriter writer=response.getWriter();
			// creating the html page
			writer.println("<html><body>");
			writer.println("<h1>Result of all Matching</h1>");
			//XPathEngineImpl obj_xPath_engine= new XPathEngineImpl();
			if(dom!=null)
			{
				writer.println("<table><tr><th>xpath</th><th>matched_result</th></tr>");
				System.out.println("Before evaluate function.....");
				boolean[] result = obj.evaluate(dom);
				System.out.println("After evaluate");
				for(int i = 0; i < result.length; i ++)
				{
					System.out.println("Inside for + " + final_xPaths[i]);
					writer.println("<tr><td>" + final_xPaths[i] + "</td><td class='green'>"+result[i]+"</td><tr>");
				}		
			}	
			else
			{
				writer.println("<br>No Such File Exists");
			}	

		}
		catch(Exception e)
		{
			System.out.println("Exception occurred while creating an object of DOM parser......"+e);
		}


	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException
	{
		/* TODO: Implement user interface for XPath engine here */
		//making a HTML form
		response.setContentType("text/html");
		PrintWriter output=response.getWriter();
		StringBuffer displayStr= new StringBuffer("<!doctype html>\n");
		displayStr.append("<html>\n<head><title></title></head><body>\n");
		displayStr.append("<form action='xpath' method='POST'>\n");
		displayStr.append("XPath: <input type='text' name='xpath' id='xpath'><br/>");
		displayStr.append("URL: <input type='text' name='url' id='url'><br/>");
		displayStr.append("<input type='submit' value='Submit' />");
		displayStr.append("</form>\n</body>\n</html>");
		output.println(displayStr);
	}

}









