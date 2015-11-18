package edu.upenn.cis455.storage;


import java.util.ArrayList;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;

/**
 * 
 * @author cis455
 *creating the UserEntityClass to denote User as an entity, with username, password, and channel names associated 
 *with this entity
 */
@Entity
public class UserEntityClass 
{
	@PrimaryKey	
	private String username;	
	private String passwd;
	private ArrayList<String> channel_name;

	public UserEntityClass()
	{
		channel_name=new ArrayList<String>();  
	}

	public UserEntityClass(String name, String password)
	{
		this.username=name;
		this.passwd=password;
		channel_name=new ArrayList<String>(); 
	}
	/**
	 * setter method to set primary key 
	 * @param name
	 */
	public void setUsername(String name)
	{
		this.username=name;
	}
	/**
	 * setter mathod to set password
	 * @param pwd
	 */

	public void setPassword(String pwd)
	{
		this.passwd=pwd;
	}

	/**
	 * set list of channel names associated with each user
	 * @param channelNames
	 */
	public void setChannelNames(ArrayList<String> channelNames)
	{
		this.channel_name=channelNames;
	}

	/**
	 * returns username
	 * @return
	 */
	public String getUsername()
	{
		return username;
	}
	/**
	 * returns password
	 * @return
	 */

	public String getPassword()
	{
		return passwd;
	}

	/**
	 * returns the list of channel names associated with the user
	 * @return
	 */

	public ArrayList<String> getChannelNames()
	{
		return channel_name;
	}
	/**
	 * check if the channel name associated with the given user exists in the ChannelEntityClass
	 * @param ch
	 * @return
	 */

	public boolean checkChannelName(ChannelEntityClass ch)
	{
		String channelName=ch.getChannelName();
		boolean flag=channel_name.contains(channelName);
		return flag;

	}

	public void putChannel(String cname)
	{
		if(this.channel_name == null)
			this.channel_name = new ArrayList<String>();
		this.channel_name.add(cname);
	}

}
