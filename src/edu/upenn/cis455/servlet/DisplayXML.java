package edu.upenn.cis455.servlet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;

import edu.upenn.cis455.storage.ChannelEntityClass;
import edu.upenn.cis455.storage.CrawledUrlEntity;
import edu.upenn.cis455.storage.DBWrapper;

public class DisplayXML extends HttpServlet
{
	public void doGet(HttpServletRequest req, HttpServletResponse res)throws ServletException,IOException
	{
		HttpSession session=req.getSession(true);
		String username = (String) session.getAttribute("username"); 
		PrintWriter obj =res.getWriter();
		String channel_name=req.getParameter("channel_name");
		StringBuffer content=new StringBuffer("<!doctype html>\n");
		content.append("<html>\n<head><title></title></head><body>\n");
		content.append("WELCOME..."+username);

		ChannelEntityClass channel = DBWrapper.getChannel(channel_name);
		if(channel == null) 
			content.append("No XMLs for this channel. It's a new channel.");


		else{
			content.append("Welcome to channel:  " + channel.getChannelName() + "<br>");
			content.append("<br><br>All XMLs matched on the Channel:<br><br><br><br>");

			// begin !!!
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss");
			String xslPath = channel.getXslUrl();
			String head = "";

			content.append(head);


			content.append("<documentcollection>");

			ArrayList<String> matched_arr = channel.getMatchedUrls();
			Iterator it=matched_arr.iterator();
            //System.out.println("Size of array list...."+matched_arr.size());
            //System.out.println("First element in array list..."+ matched_arr.get(0));
			if (matched_arr != null)
			{	

				while(it.hasNext())
				{
					String matched_url=(String)it.next();
                    //System.out.println("Matched_url....."+matched_url);
					CrawledUrlEntity page = DBWrapper.getCrawledUrlEntity(matched_url);
                     
					String xmlContent = page.getContent();
					StringBuffer cBuf = new StringBuffer();
					if(xmlContent.contains("?>")){
						head = xmlContent.substring(0, xmlContent.indexOf("?>") + 2) +
								"<xml-stylesheet type=\"text/xsl\" href=\"" + xslPath + "\"?>";
						int startId = xmlContent.indexOf("?>") + 2;
						cBuf = new StringBuffer(xmlContent.substring(startId));
					}
					String lastCrawled = dateFormat.format(page.getLastCrawledDate()) + "T" + 
							dateFormat.format(page.getLastCrawledDate());

					// Print one document
					content.append("<document crawled=\"" + lastCrawled +
							"\" location=\"" + page.getUrl() + "\">");
					content.append(xmlContent);
					content.append("</document>");
					// End printing one document
				}
			}
			content.append("</documentcollection>");
		}


		content.append("</body></html>");
		obj.println(content);

	}

}
