package edu.upenn.cis455.servlet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import java.io.*;
public class ShowResults extends HttpServlet
{
	public void doGet(HttpServletRequest req, HttpServletResponse res)throws ServletException,IOException
    {
    	PrintWriter obj =res.getWriter();
    	StringBuffer content=new StringBuffer("<!doctype html>\n");
    	ServletContext context=getServletContext();
    	String result=(String)context.getAttribute("result_info");
    
    	content.append("<html>\n<head><title></title></head><body>\n");
	     if(result.equals("user already exists"))
	     {
	    	 content.append("<h2>User already exists......</h2><br/>");
	    	 content.append("<a href='/servlet/login'>Login</a>");
	     }	 
	     else if(result.equals("user registered successfully"))
	     {
	    	 content.append("<h2>User registered successfully......</h2><br/>");
	    	 content.append("<a href='/servlet/login'>Login</a>");
	     }
	     else if(result.equals("Invalid user"))
	     {
	    	 content.append("<h2>Not registered......</h2><br/>");
	    	 content.append("<a href='/servlet/signup'>Signup</a>");
	     }
	     else if(result.equals("Invalid channel"))
	     {
	    	 content.append("<h2>Invalid channel......</h2><br/>");
	    	 content.append("<a href='/servlet/addChannel'>Add Channel</a>");
	     }
	     else if(result.equals("Channel Exists"))
	     {
	    	 content.append("<h2>Channel already exists......</h2><br/>");
	    	 content.append("<a href='/servlet/addChannel'>Add another channel</a>");
	     }
	     else if(result.equals("Channel Added Successfully"))
	     {
	    	 content.append("<h2>Channel added successfully......</h2><br/>");
	    	 content.append("<a href='/servlet/addChannel'>Back</a>");
	     }
	     else if(result.equals("logout"))
	     {
	    	 content.append("<h2>logged out....</h2><br/>");
	    	 content.append("<a href='/servlet/login'>Login</a>");
	     }
	     
	     
	    content.append("</body></html>"); 
		obj.println(content);
    	
    }
}