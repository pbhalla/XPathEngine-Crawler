package edu.upenn.cis455.storage;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import java.io.File;

import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.StoreConfig;

public class DBManagement 
{

	private static Environment myEnv;
	private static EntityStore store;
	
	public DBManagement()
	{
		
	}
	public DBManagement(File envFile)
	{
		try
		{
			// environment configuration object instantiated
			EnvironmentConfig config_obj=new EnvironmentConfig();
			StoreConfig store_config=new StoreConfig();
			config_obj.setAllowCreate(true);
			config_obj.setSharedCache(true);
			config_obj.setTransactional(true);
			store_config.setAllowCreate(true);
			
			 myEnv=new Environment(envFile,config_obj);
			 store=new EntityStore(myEnv,"EntityStore",store_config);
			
		}
		catch(DatabaseException db)
		{
			System.out.println("Exception occurred while setting up database environment...."+db);
		}
	}
	
	public Environment getEnvironment()
	{
		return myEnv;
	}
	
	public EntityStore getStore()
	{
		return store;
	}
	
	public void closeEnvironment()
	{
		try
		{

			 if(store!=null)
			    {
			    	
			    	store.close();
			    }	
			
			 if(myEnv!=null)
			    {
			    	myEnv.cleanLog();
			    	myEnv.close();
			    }	
			
		}
		catch(DatabaseException db)
		{
			System.out.println("Excepyion occurred while closing database environment...."+db);
		}
	}
	

}
