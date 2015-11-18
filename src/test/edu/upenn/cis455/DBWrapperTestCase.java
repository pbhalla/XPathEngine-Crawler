/**
 * 
 */
package test.edu.upenn.cis455;

import java.net.URL;
import java.util.Date;

import edu.upenn.cis455.storage.CrawledUrlEntity;
import edu.upenn.cis455.storage.DBWrapper;
import static org.junit.Assert.*;
import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import edu.upenn.cis455.storage.UserEntityClass;

/**
 * @author cis455
 *
 */
public class DBWrapperTestCase extends TestCase 
{

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception 
	{

	}

	/**
	 * Test method for {@link edu.upenn.cis455.storage.DBWrapper#putUser(edu.upenn.cis455.storage.UserEntityClass)}.
	 */
	@Test
	public void testPutUser()
	{
		String user_name="parul";
		String password="govind";
		UserEntityClass obj=new UserEntityClass(user_name,password);
		DBWrapper db_obj=new DBWrapper("/home/cis455/database");
		DBWrapper.putUser(obj);
		boolean containsUser=DBWrapper.containsUser("parul");
		boolean authenticate=DBWrapper.authenticateUser(user_name, password);

		assertEquals(true,containsUser);
		assertEquals(true,authenticate);

		//fail("Not yet implemented");
	}

	/**
	 * Test method for {@link edu.upenn.cis455.storage.DBWrapper#deleteCrawledUrlEntity(java.lang.String)}.
	 */
	@Test
	public void testDeleteCrawledUrlEntity() 
	{
		//fail("Not yet implemented");
		String url="https://dbappserv.cis.upenn.edu/crawltest.html";
		URL obj=null;
		try
		{
			obj=new URL(url);
		}
		catch(Exception e)
		{
			System.out.println("Malformed url exception....");
		}
		try
		{
			String content = "hello";
			String contentType="html";
			Date d=new Date();
			CrawledUrlEntity entity=new CrawledUrlEntity(url,content,d,contentType);
			DBWrapper.addCrawledUrlEntity(entity);
			DBWrapper.deleteCrawledUrlEntity(url);
			CrawledUrlEntity ob=DBWrapper.getCrawledUrlEntity(url);
			assertEquals(null,ob);
			
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred..."+e);
		}


	}

}
