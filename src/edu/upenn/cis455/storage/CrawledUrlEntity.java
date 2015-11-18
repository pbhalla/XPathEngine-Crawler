package edu.upenn.cis455.storage;

import java.util.ArrayList;
import java.util.Date;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;

@Entity
public class CrawledUrlEntity 
{
   @PrimaryKey
   private String url;
   private String content;
   private Date lastCrawledDate;
   private String contentType;
   
   public CrawledUrlEntity(String url, String content, Date d,String content_type)
   {
	   this.url=url;
	   this.content=content;
	   this.lastCrawledDate=d;
	   this.contentType=content_type;
   }
   public CrawledUrlEntity()
   {
	   
   }
   /**
    * set the primary key for the CrawledUrlEntity
    * @param url
    */
   public void setUrl(String url)
   {
	   this.url=url;
   }
   /**
    * sets the content of the HTML/XML files reterieved from the web
    * @param content
    */
   public void setContent(String content)
   {
	   this.content=content;
   }
   /**
    * set the date when the webpage was last crawled
    * @param lastCrawled
    */
   public void setLastCrawled(Date lastCrawled)
   {
	   this.lastCrawledDate=lastCrawled;
   }
   /**
    * sets the content type
    * @param contentType
    */
   public void setContentType(String contentType)
   {
	   this.contentType=contentType;
   }
   /**
    * returns the url
    * @return
    */
   public String getUrl()
   {
	   return url;
   }
   /**
    * return the content of the HTML/XML webpage crawled
    * @return
    */
   public String getContent()
   {
	   return content;
   }
   /**
    * return the date when the page was last crawled
    * @return
    */
   public Date getLastCrawledDate()
   {
	   return lastCrawledDate;
   }
   public String getContentType()
   {
	   return contentType;
   }
   
}
