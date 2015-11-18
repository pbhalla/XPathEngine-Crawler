package edu.upenn.cis455.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.upenn.cis455.storage.ChannelEntityClass;
import edu.upenn.cis455.storage.DBWrapper;
import edu.upenn.cis455.storage.UserEntityClass;

public class CrawlerServlet extends HttpServlet 
{
	final int LOGIN=1;
	final int REGISTER=2;
	final int CHANNEL=3;
	final int XML=4;
	final int LOGOUT=5;
	final int DELETE=6;
	DisplayPages dp=null;

	public void doGet(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException
	{
		String hidden_param=req.getParameter("hidden");
		
		dp=new DisplayPages();
		PrintWriter obj= res.getWriter();
		res.setContentType("text/html");
		obj.println("<html><body>");
		if(hidden_param == null)
			dp.displayLogin(obj);
		else
		{   
			int hidden_field=Integer.parseInt(hidden_param);

			switch(hidden_field)
			{
			case LOGIN : dp.displayLogin(obj);
			break;
			case REGISTER: dp.displaySignupPage(obj);
			break;
			case CHANNEL: displayChannels(obj, req);
			break;
			/*case XML: dp.displayXmlPage(obj,req);
			break;*/
			case LOGOUT: dp.displayLogout(obj,req);
			break;
			case DELETE: displayDeleteChannel(req,res);
			break;
			default: dp.displayLogin(obj);        
			}
		}
		obj.println("</body></html>");

	}
	public void doPost(HttpServletRequest req, HttpServletResponse res)throws ServletException, IOException
	{
		String hidden_param=req.getParameter("hidden");
		if(hidden_param==null)
		{
			System.out.println("no user logged in");
			doGet(req,res);
			return;
		}
		String user_name;
		String password;
		setUpDB();
		PrintWriter obj=res.getWriter();
		int hidden_field=Integer.parseInt(hidden_param);
		switch(hidden_field)
		{

		case 1://login user
			user_name = req.getParameter("name");
			password = req.getParameter("pwd");
			if(DBWrapper.authenticateUser(user_name, password))
			{
				
				req.getSession().setAttribute("currently_logged_user", user_name);
				res.sendRedirect(req.getContextPath() + "/crawler?hidden=3");
			}   
			else
			{
				dp.showResultPage("incorrect", obj);
			}	


		case 2: //register user
			user_name = req.getParameter("name");
			password = req.getParameter("pwd");
			if(user_name.isEmpty() || password.isEmpty())
			{
				doGet(req, res);
			}
			if(DBWrapper.containsUser(user_name))
			{
				// user already exists in db
				dp.showResultPage("user_exists", obj);
			}
			else
			{
				UserEntityClass user = new UserEntityClass(user_name, password);
				DBWrapper.putUser(user);

				dp.showResultPage("register_success", obj);
			}

			break;
			
		case 3: String cname = req.getParameter("channel_name");
		        String xpaths = req.getParameter("xpaths");
		        String xslURL = req.getParameter("xsl_url");

		// Add channels
		if(cname == null || xpaths == null || xslURL == null)
		{
			dp.showResultPage("incorrect_channel", obj);
		}
		if(DBWrapper.checkChannel(cname))
		{
			dp.showResultPage("channel_exists", obj);
		}
		ArrayList<String>xpathsArray = new ArrayList<String>();
		for(String piece : xpaths.split("\\s+")){
			xpathsArray.add(piece);
		}
		ChannelEntityClass newChannel = new ChannelEntityClass(cname, xpathsArray, xslURL);
		String username = (String) req.getSession().getAttribute("logged_user");
		DBWrapper.putChannel(newChannel);

		// update user
		UserEntityClass owner = DBWrapper.getUser(username);
		owner.putChannel(cname);
                                                                                                                                                                                                                                                                                                                                                                                                                                                                     		DBWrapper.putUser(owner);

		
		
		doGet(req, res);
		break;
			
		}

	}

	/**
	 * function used to set up the db environment
	 */
	public void setUpDB()
	{
		String db_dir = getServletConfig().getServletContext().getInitParameter("BDBstore");
		new DBWrapper(db_dir);
	}
	public void displayDeleteChannel( HttpServletRequest req, HttpServletResponse res)
	{
		try
		{
			String user_name = (String) req.getSession().getAttribute("currently_logged_user");
			if(user_name == null)
				doGet(req, res);
			String channel_name = req.getParameter("channel_name");
			DBWrapper.deleteChannel(user_name, channel_name);
			dp.showResultPage("deleted", res.getWriter());
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred...."+e);
		}
	}


	public void displayChannels(PrintWriter obj,HttpServletRequest req)
	{

		String logged_user = (String) req.getSession().getAttribute("currently_logged_user");
		setUpDB();
		ArrayList<ChannelEntityClass> channels = DBWrapper.getAllChannels();
		if(channels == null || channels.size() == 0) 
			obj.println("No channels available.");
		else{
			obj.println("<br><br>All Channels on the Systems:<table>");

			// if not logged, only can see names
			if(logged_user == null){
				obj.println("<tr><th>Channel Name</th></tr>");
				for(ChannelEntityClass ch : channels)
				{
					obj.println("<tr><td>" + ch.getChannelName()+ "</td></tr>");
				}
				obj.println("You are not logged in. To see channel contents, please <a href='?page=1'>Log In</a>");
			}
			// if logged users, they can add/delete
			else{  
				obj.println("<tr><th>Channel Name</th><th>page</th><tr>");
				for(ChannelEntityClass ch : channels){
					String channel_name = ch.getChannelName();

					// display add/delete pages if this channel belongs to user
					if(DBWrapper.checkUserChannel(logged_user, channel_name)){

						obj.println("<tr><td><a href='?hidden=4&channel_name=" + channel_name + "'>" + channel_name + 
								"</a></td><td><a href='?hidden=6&channel_name=" + channel_name + "'>delete</a></td></tr>");
					}
				}
			}
			obj.println("</table><br><br><br>");
		}

		// if logged, also show add channel form
		if(logged_user != null)
			dp.addChannelPage(obj);
	}

	/*
	public void displayXmlPage(PrintWriter obj,HttpServletRequest req)
    {
		String logged_user = (String) req.getSession().getAttribute("currently_logged_user");
		String cname = req.getParameter("cname");
		
		System.out.println("[debug!!!!]\t" + loggedUser);
		System.out.println("[debug !!!!!]\t" + cname);
		
		setDBRoot();
		Channel channel = DatabaseUtil.getChannel(cname);

		// if not logged, only can see names
		if(loggedUser == null)
			out.println("You are not logged in. To see channel contents, please <a href='?page=1'>Log In</a>");
		else if(channel == null) 
			out.println("No XMLs for this channel. It's a new channel.");

		// if logged users & valid channel, display matched XMLs
		else{
			out.println("Welcome to channel:  " + channel.getName() + "<br>");
			out.println("<br><br>All XMLs matched on the Channel:<br><br><br><br>");
			
			// begin !!!
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss");
			String xslPath = channel.getXSLURL();
			String head = "";
			
			out.println(head);
			out.println("<documentcollection>");
			

			for(String mURL : channel.getMatchedURLs()){
				
				Webpage webpage = DatabaseUtil.getWebpage(mURL);
				
				String xmlContent = webpage.getContent();
				StringBuffer cBuf = new StringBuffer();
				if(xmlContent.contains("?>")){
					head = xmlContent.substring(0, xmlContent.indexOf("?>") + 2) +
							"<xml-stylesheet type=\"text/xsl\" href=\"" + xslPath + "\"?>";
					int startId = xmlContent.indexOf("?>") + 2;
					cBuf = new StringBuffer(xmlContent.substring(startId));
				}
				String lastCrawled = dateFormat.format(webpage.getLastCrawled()) + "T" + 
						dateFormat.format(webpage.getLastCrawled());
			    
				// Print one document
				out.println("<document crawled=\"" + lastCrawled +
			    		"\" location=\"" + webpage.getURL() + "\">");
			    out.println(xmlContent);
				out.println("</document>");
				// End printing one document
			}
			
			out.println("</documentcollection>");
		}

		// if logged, also show add channel form
		if(loggedUser != null)showAddChannelForm(out);
	}
    	
    }
    */

}


