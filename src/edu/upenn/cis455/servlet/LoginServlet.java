package edu.upenn.cis455.servlet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import java.io.*;

import edu.upenn.cis455.storage.DBWrapper;

public class LoginServlet extends HttpServlet
{
	public void init()
	{
		DBWrapper dbObj=new DBWrapper(getServletContext().getInitParameter("BDBstore"));
	       
	}
    public void doGet(HttpServletRequest req, HttpServletResponse res)throws ServletException,IOException
    {
    	PrintWriter obj =res.getWriter();
    	StringBuffer content=new StringBuffer("<!doctype html>\n");
    	content.append("<html>\n<head><title></title></head><body>\n");
    	content.append("<h1><p align='middle'>CIS-455 Crawler</p></h1>");
    	content.append("<h1><p align='middle'>pbhalla@seas.upenn.edu</p></h1>");
		content.append("<form action='login' method='POST'>\n");
		content.append("Username: <br/><br/>");
		content.append("<input type='text' name='username' id='username'><br/><br/>");
		content.append("Password: <br/><br/>");
		content.append("<input type='text' name='password' id='password'><br/><br/>");
		content.append("<input type='submit' value='Submit' /><br/><br/>");
		content.append("<a href='/servlet/signup'>Signup</a><br/><br/>");
		content.append("<a href='/servlet/channelDisplay'>Display Channels</a><br/>");
		content.append("</form>\n</body>\n</html>");
		
		obj.println(content);
    	
    }
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException,IOException
    {
       String user_name=req.getParameter("username");
       String password=req.getParameter("password");
       ServletContext context=getServletContext();
       
       // checking authentication of user from the DB
       boolean isAuthentic=DBWrapper.authenticateUser(user_name,password);
       if(isAuthentic)
       {
    	   HttpSession session=req.getSession(true);
    	   session.setAttribute("username",user_name); 
    	   res.sendRedirect("/servlet/addChannel");
    	   return;                                                                                                                   
       }   
       else
       {
    	   context.setAttribute("result_info","Invalid user");
    	   res.sendRedirect("/servlet/showResults");
		   return;	
       }   
    	
    }
    
}
