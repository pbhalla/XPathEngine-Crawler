package edu.upenn.cis455.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import edu.upenn.cis455.storage.ChannelEntityClass;
import edu.upenn.cis455.storage.DBWrapper;
import edu.upenn.cis455.storage.UserEntityClass;

public class AddChannel extends HttpServlet
{
	public void doGet(HttpServletRequest req, HttpServletResponse res)throws ServletException,IOException
	{

		HttpSession session=req.getSession(true);
		String username = (String) session.getAttribute("username"); 
		PrintWriter obj =res.getWriter();
		StringBuffer content=new StringBuffer("<!doctype html>\n");
		content.append("<html>\n<head><title></title></head><body>\n");
		content.append("WELCOME..."+username);
		content.append("<br/><form action='addChannel' method='POST'>\n");
		content.append("Channel Name: <br/><br/>");
		content.append("<input type='text' name='channel_name' id='channel_name'><br/><br/>");
		content.append("XPaths: <br/><br/>");
		content.append("<input type='text' name='xpaths' id='xpaths'><br/><br/>");
		content.append("XSL URL: <br/><br/>");
		content.append("<input type='text' name='xsl_url' id='xsl_url'><br/><br/>");
		content.append("<input type='submit' value='Submit' /><br/><br/>");
		content.append("<a href='/servlet/displayChannel'>Display Channels</a><br/><br/>");
		content.append("<a href='/servlet/deleteChannel'>Delete Channel</a><br/><br/>");
		content.append("<a href='/servlet/logout'>Logout</a><br/><br/>");
		content.append("</form>\n</body>\n</html>");
		obj.println(content);

	}
	public void doPost(HttpServletRequest req, HttpServletResponse res)throws ServletException,IOException
	{
		String cname = req.getParameter("channel_name");
		String xpaths = req.getParameter("xpaths");
		String xslURL = req.getParameter("xsl_url");
        ServletContext context=getServletContext();
        
		// Add channels
		if(cname == null || xpaths == null || xslURL == null)
		{
			context.setAttribute("result_info", "Invalid channel");
			res.sendRedirect("/servlet/showResults");
			return;
		}
		if(DBWrapper.checkChannel(cname))
		{
			context.setAttribute("result_info", "Channel Exists");
			res.sendRedirect("/servlet/showResults");
			return;
		}
		ArrayList<String>xpathsArray = new ArrayList<String>();
		for(String piece : xpaths.split("\\s+")){
			xpathsArray.add(piece);
		}
		ChannelEntityClass newChannel = new ChannelEntityClass(cname, xpathsArray, xslURL);
		String username = (String) req.getSession().getAttribute("username");
		if(newChannel==null)
		{
			System.out.println("newChannel null");
		}	
		DBWrapper.putChannel(newChannel);
        System.out.println("channel added");
		// update user
		UserEntityClass owner = DBWrapper.getUser(username);
		owner.putChannel(cname);
		System.out.println("channel name added");
		DBWrapper.putUser(owner);
		System.out.println("User updated");
		context.setAttribute("result_info", "Channel Added Successfully");
		res.sendRedirect("/servlet/showResults");
		return;
                 
	}

}

