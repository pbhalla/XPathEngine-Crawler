package edu.upenn.cis455.servlet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import java.io.*;

import edu.upenn.cis455.storage.DBWrapper;

public class DeleteChannel extends HttpServlet 
{
	public void doGet(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException
	{
		HttpSession session=req.getSession(false);
		String username = (String) session.getAttribute("username"); 
		PrintWriter obj =res.getWriter();
		String channel_name=req.getParameter("channel_name");
		StringBuffer content=new StringBuffer("<!doctype html>\n");
		content.append("<html>\n<head><title></title></head><body>\n");
		content.append("WELCOME..."+username+"\n");
		//content.append("<form action='deleteChannel' method='POST'>\n");
		//content.append("Delete channel......"+channel_name);
		//content.append("<input type='submit' value='Delete' /><br/><br/>");
        //content.append("</form>\n</body>\n</html>");
		
		DBWrapper.deleteChannel(username, channel_name);
		content.append(channel_name+" deleted....");
		obj.println(content);		

	}
	public void doPost(HttpServletRequest req, HttpServletResponse res)throws ServletException,IOException
	{
		/*
		
		HttpSession session=req.getSession(false);
		String channel_name=req.getParameter("channel_name");
		String username = (String) session.getAttribute("username"); 
		System.out.println(username);
		System.out.println(channel_name);
		DBWrapper.deleteChannel(username, channel_name);
		//showResultPage("deleted", resp.getWriter());
		*/
	}
}
