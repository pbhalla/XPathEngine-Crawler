package edu.upenn.cis455.storage;


import java.util.ArrayList;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
/**
 * creating ChannelEntity which will have channelName, list of xPaths, and list of matched urls associated with it
 * @author cis455
 *
 */
@Entity
public class ChannelEntityClass 
{
	@PrimaryKey
	private String channelName;
	private ArrayList<String> xPaths;
	private String xslUrl;
	private ArrayList<String> matchedUrls;

	/**
	 * constructor of ChannelEntityClass
	 */
	public ChannelEntityClass()
	{
		xPaths=new ArrayList<String>();
		matchedUrls= new ArrayList<String>();
	}
	public ChannelEntityClass(String channel_name,ArrayList<String> xPaths, String url)
	{
		this.channelName=channel_name;
		this.xPaths=xPaths;
		this.xslUrl=url;
	}
	/**
	 * set the primary key
	 * @param channelName
	 */
	public void setChannelName(String channelName)
	{
		this.channelName=channelName; 
	}
	/**
	 * set the array list xPaths associated with the channel name
	 * @param xPaths
	 */
	public void setXPaths(ArrayList<String> xPaths)
	{
		this.xPaths=xPaths;	  
	}
	/**
	 * set the list of matched urls associated with that channel name
	 * @param matchedUrls
	 */
	public void setMatchedUrls(ArrayList<String> matchedUrls)
	{
		this.matchedUrls=matchedUrls;
	}
	public void setXslUrl(String url)
	{
		this.xslUrl=url;
	}

	/**
	 * returns the name of the channel
	 * @return
	 */
	public String getChannelName()
	{
		return channelName;
	}
	/**
	 * returns the list of xPaths associated with that channel name
	 * @return
	 */
	public ArrayList<String> getXPaths()
	{
		return xPaths;
	}
	/**
	 * returns the list of matched urls associated with the channel name
	 * @return
	 */
	public ArrayList<String> getMatchedUrls()
	{
		return matchedUrls;
	}

	public void addMatchedURL(String url)
	{
		if(matchedUrls == null)
			matchedUrls = new ArrayList<String>();
		matchedUrls.add(url);
	}

	public String getXslUrl()
	{
		return xslUrl;
	}
}
