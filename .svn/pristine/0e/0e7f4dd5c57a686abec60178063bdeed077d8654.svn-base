package test.edu.upenn.cis455;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.upenn.cis455.servlet.HttpClient;
import edu.upenn.cis455.xpathengine.XPathEngineImpl;
import edu.upenn.cis455.xpathengine.XPathEngineFactory;


public class TestCases extends TestCase
{
	private String servlet = "http://localhost:8080/HW2/xpath";
	private String url = "http://cis555.co.nf/xml/courses.xml";

	public void testText() throws UnknownHostException, IOException, InterruptedException
	{
		String xpath = "/courses/course/name[text() = \"Internet and Web Systems\"]";
		XPathEngineImpl engine = new XPathEngineImpl();
		String[] xpaths = new String[1];
		xpaths[0] = xpath;
		engine.setXPaths(xpaths);
		Document dom = obtainDomObject(url);
		boolean[] existed = engine.evaluate(dom);
		assertEquals(true, existed[0]);
	}

	public void testContains() throws UnknownHostException, IOException, InterruptedException
	{
		String xpath =  "/courses/course/term[contains(text(), \"in g\"]";
		XPathEngineImpl engine = new XPathEngineImpl();
		String[] xpaths = new String[1];
		xpaths[0] = xpath;
		engine.setXPaths(xpaths);
		Document dom = obtainDomObject(url);
		boolean[] existed = engine.evaluate(dom);
		assertEquals(false, existed[0]);
	}

	public void testAtt() throws UnknownHostException, IOException, InterruptedException
	{
		String xpath =  "/courses/course/term[text() = \"spring\"]";;
		XPathEngineImpl engine = new XPathEngineImpl();
		String[] xpaths = new String[1];
		xpaths[0] = xpath;
		engine.setXPaths(xpaths);
		Document dom = obtainDomObject(url);
		boolean[] existed = engine.evaluate(dom);
		assertEquals(true, existed[0]);
	}


	public Document obtainDomObject(String url)
	{
		URL urlObj = null ;
		try
		{
			//URL urlObj ;
			String url_decode=URLDecoder.decode(url,"UTF-8");
			if(url.startsWith("http://")|| url.startsWith("HTTP://"))
			{
				url=url_decode;
			}	
			else
			{
				url="http://"+url_decode;
			}	
			urlObj = new URL(url);
		}
		catch(Exception e)
		{
			System.out.println("Exception occurred.....");  
		}


		{
			Document doc=null;
			try
			{
				System.out.println("creating dom obj");
				doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(urlObj.openStream());
				return doc;
			}
			catch(Exception e)
			{
				System.out.println("Exception occurred while creating dom for xml...."+e);
			}
		}
		return null;	
	}	

}

