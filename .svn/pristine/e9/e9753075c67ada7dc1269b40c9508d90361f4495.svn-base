package edu.upenn.cis455.servlet;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DisplayPages 
{
	public DisplayPages()
	{
		
	}
	/**
	 * function to display the login page
	 * @param obj
	 */
    public void displayLogin(PrintWriter obj)
    {
    	StringBuffer store_string=new StringBuffer("");
    	obj.println("form action='crawler' method='POST'");
    	obj.println("<h1>XPath Crawler: Log In</h1>");
    	obj.println("<p>cis455Crawler-------Parul Bhalla-------pbhalla@seas.upenn.edu</p><br/>");
    	obj.println("Username: <input type='text' id='name' name='name' value='" + "' /><br/>");
    	obj.println("Password :<input type='text' id='pwd' name='pwd' value='" +  "'/><br>");
    	obj.println("<br><input type='submit' name='login' value='login'/>");
    	obj.println("<input type='hidden' name='hidden' value='1'/>");
    	obj.println("</form>");
    	
    	// sign up
    	obj.println("<form action='crawler' method='GET'>");
		obj.println("<input type='submit' name='action' value='register'/>");
		obj.println("<input type='hidden' name='hidden' value='2'/></form>");
		
		// show channels
		obj.println("<form action='crawler' method='GET'>");
		obj.println("<input type='submit' name='action' value='show channels'/>");
		obj.println("<input type='hidden' name='hidden' value='3'/></form>");
		
		//logout
		obj.println("<form action='crawler' method='GET'>");
		obj.println("<input type='submit' name='action' value='logout'/>");
		obj.println("<input type='hidden' name='hidden' value='5'/></form>");
    	   	
    }
    /**
     * method to display signup page
     * @param obj
     */
    public void displaySignupPage(PrintWriter obj)
    {
    	obj.println("<form action='crawler' method = 'POST'");
		obj.println("<h1>Create New Account</h1>");
		obj.println("Username: <input type='text' name='name' value='" + "' /><br/>");
		obj.println("Password: <input type='text' name='pwd'  value='" +  "'/><br/>");
		obj.println("<input type='submit' value='OK'/>");
		obj.println("<input type='hidden' name='hidden' value='2'/>");
		obj.println("</form>");
    }
    
    public void addChannelPage(PrintWriter obj)
    {
    	obj.println("<form action='crawler' method = 'POST'>");
		obj.println("<h1>Add Channel.........</h1>");
		obj.println("Channel Name: <input type='text' name='channel_name' id='channel_name' value='" + "' /><br/>");
		obj.println("XPaths: <input type='text' name='xpaths' id='xpaths' value='" + "' /><br/>");
		obj.println("XSL URL: <input type='text' name='xsl_url' id='xsl_url' value='" +  "'/><br/>");
		obj.println("<input type='submit' value='Add'/>");
		obj.println("<input type='hidden' name='hidden' value='3'/>");
		obj.println("</form>");
		obj.println("<a href='crawler'>Back</a>");
    }
    
   
    
    public void displayLogout(PrintWriter obj, HttpServletRequest req)
    {
    	req.getSession().setAttribute("crrently_logged_user", null);
		showResultPage("logout", obj);
		obj.println("You have been logged out<a href='crawler?page=1>Back</a>");
    }
   
    
    public void showResultPage(String result, PrintWriter obj)
    {
    	String result_info = "";
		obj.println("<html><body>");
		
		if(result.equals("user_exists"))
			result_info = "User already existed. <a href='crawler'>Back</a>";
		if(result.equals("register_success"))
			result_info = "You have successfully registered. Thanks! + <a href='crawler'>Back</a>";
		if(result.equals("incorrect"))
			result_info = "Incorrect username or password. <a href='crawler'>Back</a>";
		if(result.equals("incorrect_channel"))
			result_info = "Channel/XPaths/XSL URL cannot be empty.<a href='crawler'>Back";
		if(result.equals("channel_exists"))
			result_info = "Channel already existed. <a href='crawler'>Back</a>";
		if(result.equals("channel_deleted"))
			result_info = "Channel deleted. <a href='crawler?hidden=3'>Back</a>";
		if(result.equals("logout"))
			result_info = "You've been sucessfully logged out. <a href='crawler?hidden=1'>Back</a>";
		obj.println(result_info + "</body></html>");
    	
    }
    
}
