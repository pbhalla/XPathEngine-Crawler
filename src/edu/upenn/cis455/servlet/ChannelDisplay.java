package edu.upenn.cis455.servlet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import java.io.*;
import java.util.ArrayList;

import edu.upenn.cis455.storage.ChannelEntityClass;
import edu.upenn.cis455.storage.DBWrapper;

public class ChannelDisplay extends HttpServlet 
{
	public void doGet(HttpServletRequest req, HttpServletResponse res)throws ServletException,IOException
	{
		PrintWriter obj=res.getWriter();
		StringBuffer content=new StringBuffer("<!doctype html>\n");
		content.append("<html>\n<head><title></title></head><body>\n");
		ArrayList<ChannelEntityClass> channels = DBWrapper.getAllChannels();
		if(channels == null || channels.size() == 0) 
			content.append("No channels available.");
		else
		{
			content.append("<br><br>All Channels on the Systems:<table>");


			content.append("<tr><th>Channel Name</th></tr>");
			for(ChannelEntityClass ch : channels)
			{
				content.append("<tr><td>" + ch.getChannelName()+ "</td></tr>");
			}
			content.append("You are not logged in. To see channel contents, please <a href='/servlet/login'>Log In</a>");
		}
		content.append("</table>\n</body>\n</html>");
		obj.println(content);
	}


}
