package edu.upenn.cis455.xpathengine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;
import java.util.TreeMap;
import java.util.regex.*;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XPathEngineImpl implements XPathEngine 
{
	String[] xPaths=null;
	TreeMap<String,ArrayList<String>> store_test_string=null;

	public XPathEngineImpl() {
		// Do NOT add arguments to the constructor!!
	}

	public void setXPaths(String[] s) 
	{
		/* TODO: Store the XPath expressions that are given to this method */
		this.xPaths=s;
		System.out.println(xPaths.length);
		for (int i=0;i<xPaths.length;i++)
			System.out.println(xPaths[i]);
	}

	public boolean isValid(int i) 
	{
		/* TODO: Check which of the XPath expressions are valid */
		boolean flag=true;
		String xpath=xPaths[i];
		store_test_string=new TreeMap<String,ArrayList<String>>();

		// check the if the xPath contains axis and if axis is followed by step also

		boolean axisValid=isValidAxis(xpath);
		if (axisValid==false)
			flag=false;
		boolean stepValid=isValidStep(xpath.substring(1)); 
		return flag;
	}
	public boolean isValidAxis(String path)
	{
		if((!path.startsWith("/"))||(path.equals("/")))
			return false;
		else
			return true;
	}
	public boolean isValidStep(String step)
	{
		// checking whether the input xPath is valid or not
		//dividing the step into sub levels
		ArrayList<String> subLevels= obtainSubLevels(step);

		//checking the validity of each sublevel by iterating the array list
		Iterator it=subLevels.iterator();
		while(it.hasNext())
		{
			String element=(String)it.next();
			//printing the sublevel
			System.out.println("Sublevel.........."+element);
			boolean validSubLevel=isValidSubLevel(element);
			if(!validSubLevel)
				return false;  
		}	  
		return true;

	}
	// creating a function to check the validity of each sub level

	public boolean isValidSubLevel(String element)
	{
		String matchingPattern= "(\\s)*([A-Z_a-z][A-Z_a-z-.0-9]*)(\\s)*(\\[.+\\])*";
		String element_lower=element.toLowerCase();
		if(element_lower.startsWith("xml"))
			return false;
		if(Pattern.compile(matchingPattern).matcher(element).matches())
		{
			boolean quotes=false;
			boolean enterTest=false;
			boolean foundNode=false;
			String node_name=null;
			StringBuffer testString=new StringBuffer();
			int length=element.length();
			Stack<Character> stack=new Stack<Character>();
			for(int i=0;i<length;i++)
			{
				char x=element.charAt(i);
				switch(x)
				{
				case '"':quotes=!quotes;
				break;
				case '[': if(!quotes)
				{
					//push brackets on stack
					stack.push(x);
					// we have entered the 'test'
					enterTest=true;
					// checking if the nodename has not been found already
					if(!foundNode)
					{
						node_name=element.substring(0,i);
						foundNode=true;
					}   

				}
				break;
				case ']': if(!quotes)
				{
					stack.pop();
					if(stack.isEmpty())
					{
						enterTest=false;
						boolean validTest=isValidTest(testString.toString());
						if(!validTest)
							return false;
						if(store_test_string.containsKey(node_name))
						{
							ArrayList<String> test_string=store_test_string.get(node_name);
							test_string.add(testString.toString());
							store_test_string.put(node_name,test_string);
						}   
						else
						{
							ArrayList<String> test_string=new ArrayList<String>();
							test_string.add(testString.toString());
							store_test_string.put(node_name, test_string);
						}   

						//############################################################         	   
					}   
				}
				break;

				}
				// creating the test string if already inside the test string
				if(enterTest)
				{
					testString.append(x);
				}	
			}	 
			return true;

		}	  
		// matching of regex fails...
		return false;

	}

	public boolean isValidTest(String testString)
	{
		// removing the square brackets from the testString

		String testString_withoutBrackets=testString.substring(1, testString.length());

		/* regex to test the testString
	     1st for: text () = "..."
	     2nd for: contains (text(), "..." )
	     3rd for: @attname = "..." 
		 */

		String matchingString = "(((\\s)*text(\\s)*\\((\\s)*\\)(\\s)*\\=(\\s)*\\\"(.*?)\\\"(\\s)*)|"  +   
				"((\\s)*contains(\\s)*\\((\\s)*text(\\s)*\\((\\s)*\\)(\\s)*,(\\s)*\\\"(.*?)\\\"(\\s)*\\))|" +
				"((\\s)*\\@(\\s)*([A-Z_a-z][A-Z_a-z0-9-.]*)(\\s)*\\=\\\"[^\\\"]+\\\"(\\s)*))";

		// checking if testString is one of these base cases

		if(Pattern.compile(matchingString).matcher(testString_withoutBrackets).matches())
		{
			// the test string matches with one of the base cases
			return true;
		}
		else
		{
			//the test string contains 'step' and need further validation
			return this.isValidStep(testString_withoutBrackets);
		}	  

	}

	public ArrayList<String> obtainSubLevels(String step)
	{
		ArrayList<String> subLevels=new ArrayList<String>();

		//check if the given step contains only the name of the node
		if(!step.contains("/"))
		{
			// if it is just the node name and nothing else
			subLevels.add(step);
			return subLevels;
		}	  
		else
		{
			StringBuffer level=new StringBuffer();
			// creating a stack to check proper opening-closing quotes/braces
			boolean quotes=false;
			boolean escape = false;
			Stack<Character> stack= new Stack<Character>();
			// checking each character of the given level

			//length of the string
			int length=step.length();
			for(int i=0; i<length;i++)
			{
				char x= step.charAt(i);
				switch(x)
				{
				case '\\': if (quotes) {
					escape = true;
				}
				break;
				case '"': if (escape){
					escape = false;
					break;
				}
				else {
					quotes = !quotes;
				}
				break;
				case '[':if(!quotes)
				{
					stack.push(x);

				}
				break;
				case ']':if(!quotes)
				{
					if(!stack.isEmpty())
						stack.pop(); 
				}
				break;
				case '/': if(!quotes)
				{
					// slash cannot be there inside the square brackets.
					if(!stack.isEmpty())
						break;
					subLevels.add(level.toString());
					level.setLength(0);
					continue;
				}
				break;
				}
				level.append(x);

			}
			subLevels.add(level.toString());
			return subLevels;
		}	  

	}

	public boolean[] evaluate(Document d)
	{ 
		/* TODO: Check whether the document matches the XPath expressions */
		// find the size of the array which is storing all the xpaths....
		System.out.println("Inside evaluate......here....");
		System.out.println("Size.....");

		int size= this.xPaths.length;  
		System.out.println("Size......."+size);
		//creating a boolean array to store the result of each xPath....
		boolean[] match_result=new boolean[size];


		for(int i=0;i<size;i++)
		{
			// check if the xPath is valid or not
			boolean valid_xPath=isValid(i);
			//if xPath is valid
			System.out.println("Valid xPath....."+valid_xPath);
			if(valid_xPath)
			{
				System.out.println("xPath found valid......");
				ArrayList<Node> current_node_list=new ArrayList<Node>();
				Node root=d.getDocumentElement();

				String removeAxis=xPaths[i].substring(1);
				current_node_list.add(root);
				match_result[i]= match_step_with_document(removeAxis,current_node_list);
			}	

		}		


		return match_result; 
	}

	public boolean match_step_with_document(String stepString,ArrayList<Node> current_level_nodes)
	{
		// obtain all the levels of the given step String
		boolean flag=false;
		ArrayList<String> subLevels= obtainSubLevels(stepString);

		ArrayList<Node> childNodes=current_level_nodes;
		Iterator it=subLevels.iterator();
		while(it.hasNext())
		{
			String level=(String)it.next();
			childNodes=match_level_with_document(level,childNodes);

			if(childNodes != null)
			{
				// find the size of child nodes returned
				if(childNodes.size()>0)
					flag=true; 

			}
			else
			{
				return false;
			}	  
		}	  
		return flag;
	}

	public ArrayList<Node> match_level_with_document(String level, ArrayList<Node> nodes_to_match_with)
	{
		// check if the level received is just a node name or it contains test

		String node_name;
		boolean has_test=false;
		ArrayList<Node> child_nodes=new ArrayList<Node>();

		if(level.contains("["))
		{
			// contains test
			node_name=level.substring(0,level.indexOf("[")).trim();
			has_test=true;
		}	  
		else
		{
			node_name=level.trim();
		}	  
		//match the levels
		Iterator node=nodes_to_match_with.iterator();
		while(node.hasNext())
		{
			Node current_node=(Node)node.next();
			if(has_test)
			{
				// if the level contains test...retrieve the test strings from the tree map
				if(store_test_string.containsKey(node_name))
				{	 
					ArrayList<String> test_string=store_test_string.get(node_name);

					// traverse the test_string array list and check its matching
					Iterator tests=test_string.iterator();
					while(tests.hasNext())
					{
						String testString=(String)tests.next();
						boolean test_matched=match_test_with_document(testString,current_node);
						if(test_matched)
						{
							// match found hence find the child nodes of the given node
							NodeList children_node_list=current_node.getChildNodes();

							// adding all the child nodes to the array list

							int len_node_list=children_node_list.getLength();
							for(int i=0;i<len_node_list;i++)
							{
								child_nodes.add(children_node_list.item(i));	
							}		
						}	 
						else
							continue; 
					}	 
				}
			}	 
			else
			{
				String current_node_name=current_node.getNodeName();

				//match the level with the contents of the current node

				if(node_name.equals(current_node_name))
				{
					// match found hence find the child nodes of the given node
					NodeList children_node_list=current_node.getChildNodes();

					// adding all the child nodes to the array list

					int len_node_list=children_node_list.getLength();
					for(int i=0;i<len_node_list;i++)
					{
						child_nodes.add(children_node_list.item(i));	
					}		
				}
				else
				{
					continue;
				}	
			} 	 
		}
		return child_nodes;


	}

	public boolean match_test_with_document(String testString, Node currentNode)
	{
		boolean test_matched=false;

		// removing the square braces from the testString obtained

		String test_string=testString.substring(1,testString.length()).trim();

		// regex for 3 base cases

		String text_matcher ="(\\s)*text(\\s)*\\((\\s)*\\)(\\s)*\\=(\\s)*\\\"(.*?)\\\"(\\s)*";
		String contains_text_matcher="(\\s)*contains(\\s)*\\((\\s)*text(\\s)*\\((\\s)*\\)(\\s)*,(\\s)*\\\"(.*?)\\\"(\\s)*\\)";
		String attribute_matcher="(\\s)*\\@(\\s)*([A-Z_a-z][A-Z_a-z0-9-.]*)(\\s)*\\=\\\"[^\\\"]+\\\"(\\s)*";

		if(Pattern.compile(text_matcher).matcher(test_string).matches())
		{
			Node childNode=currentNode.getFirstChild();
			// obtain the text in between the quoted string
			String text_test_string = test_string.split("\"")[1];
			if(childNode != null)
			{
				if(childNode.getNodeType()==Node.TEXT_NODE)
				{
					String value=childNode.getNodeValue();
					if(value.equals(text_test_string))
					{
						test_matched=true;
						return test_matched;
					}	 
				}	 

			}	 

		}	 
		else if(Pattern.compile(contains_text_matcher).matcher(test_string).matches())
		{
			Node childNode=currentNode.getFirstChild();
			// obtain the text in between the quoted string
			String text_test_string = test_string.split("\"")[1];
			if(childNode != null)
			{
				if(childNode.getNodeType()==Node.TEXT_NODE)
				{
					String value=childNode.getNodeValue();
					if(value.contains(text_test_string))
					{
						test_matched=true;
						return test_matched;
					}	 
				}	 

			}	 
		}	 
		else if(Pattern.compile(attribute_matcher).matcher(test_string).matches())
		{
			// finding the attribute key and attribute value
			String attribute_key=test_string.split("=")[0].split("@")[1].trim();
			String attribute_value=test_string.split("\"")[1].trim();
			NamedNodeMap attributes=currentNode.getAttributes();
			if(attributes!=null)
			{
				Node attribute_node=attributes.getNamedItem(attribute_key);
				if(attribute_node!=null)
				{
					String attribute_node_value=attribute_node.getNodeValue();
					if(attribute_node_value.equals(attribute_value))
					{
						test_matched=true;
						return test_matched;
					}
				}	
			}	 
		}	 
		else
		{
			// test is not one of the above base cases....further check
			NodeList children_node_list=currentNode.getChildNodes();
			ArrayList<Node> child_nodes=new ArrayList<Node>();
			// adding all the child nodes to the array list

			int len_node_list=children_node_list.getLength();
			for(int i=0;i<len_node_list;i++)
			{
				child_nodes.add(children_node_list.item(i));	
			}		
			// recursively call the match function to check if step is present in document

			return match_step_with_document(test_string,child_nodes);

		}	 
		return test_matched;

	}

}
