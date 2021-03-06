package up2p.peer.jtella;

//Logging - LOG4J
import org.apache.log4j.Logger;

//XML Reading & Writing - JDOM
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Iterator;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import protocol.com.kenmccrary.jtella.Host;
import protocol.com.kenmccrary.jtella.HostCache;
/**
 * Good XML reference book (Using JDom):
 * http://www.cafeconleche.org/books/xmljava/chapters/index.html
 * 
 * Parses the host cache XML file
 * 
 * @author Michael Yartsev (anijap@gmail.com)
 */

public class HostCacheParser {
	private Document doc;
	private Element root;
	private String hostCacheFilename;
	
	/** 
	 * An optional instance of hostCache to modify. Any hosts added or
	 * removed to the static host cache will be likewise added or removed
	 * from this dynamic host cache, but no consistency is guaranteed beyond
	 * this.
	 */
	private HostCache hostCache;
	
    /** Name of Logger. */
    public static final String LOGGER = "up2p.peer.jtella.HostCacheParser";

    /** Logger used. */
    private static Logger LOG = Logger.getLogger(LOGGER);
	
	public HostCacheParser(String hostCacheFile, HostCache hostCache) {
		this.hostCacheFilename = hostCacheFile;
		this.hostCache = hostCache;
		
		SAXBuilder builder = new SAXBuilder();
		
		try {
			doc = builder.build(hostCacheFile);
			root = doc.getRootElement();
		} 
		catch (JDOMException e) {
			LOG.error(hostCacheFile + "is not a well-formed XML document");
			LOG.error(e.getMessage());
		} 
		catch (IOException e) {
			LOG.error("Could not read " + hostCacheFile);
			LOG.error(e.getMessage());
		}
	}
	
	public HostCacheParser(String hostCacheFile) {
		this(hostCacheFile, null);
	}
	
	/**
	 * Sets the dynamic host cache that the parser should update
	 * when making changes.
	 * @param cache	The dynamic host cache that the parser should update
	 * when making changes.
	 */
	public void setDynamicHostCache(HostCache cache) {
		hostCache = cache;
	}
	
	/**
	 * @return	The dynamic host cache that this parser is set to update
	 * 			(or null if none has been set)
	 */
	public HostCache getDynamicHostCache() {
		return hostCache;
	}

	/**
	 * Returns an array of hosts from the host cache file.
	 * 
	 * @return an array of hosts.
	 */
	public Host[] getHosts() {
		LOG.info("Searching for hosts in the HostCache.");
		
		Host[] hostArray = null;

		List hosts = root.getChildren();
		
		LOG.info("	-Found " + hosts.size() + " entries.");
		
		hostArray = new Host[hosts.size()];
		Iterator hostIterator = hosts.iterator();
		
		for(int i=0; hostIterator.hasNext(); i++) {
			Element host = (Element) hostIterator.next();
			hostArray[i] = new Host(host.getAttributeValue("ip"),
					Integer.parseInt(host.getAttributeValue("port")), 1, 1);
			LOG.info("	-" + hostArray[i].getIPAddress() + ":" + hostArray[i].getPort());
			if(host.getAttributeValue("ip").equalsIgnoreCase("localhost") || 
					host.getAttributeValue("ip").equalsIgnoreCase("localhost")) {
				LOG.info("Found host cache reference to loopback interface, removing it.");
				removeHost(hostArray[i]);
				return getHosts();
			}
		}
		
		return hostArray;
	}
	
	/**
	 * Removes a host from the host cache
	 * @param host	The host to be removed
	 * @return True if the host existed and was removed, false if it was
	 * 		   not found in the host cache
	 */
	public boolean removeHost(Host host) {
		String hostString = host.getCreationIPAddress() + ":" + host.getPort();
		LOG.info("Removing host " + hostString + " from the HostCache file");
		

		List<Element> hosts = root.getChildren();
		boolean foundHost = false;
		for(Element existingHost : hosts) {
			String existingHostString = existingHost.getAttribute("ip").getValue()
				+ ":" + existingHost.getAttribute("port").getValue();
			// LOG.info("Checking against: " + existingHostString); // DEBUG
			if(existingHostString.equalsIgnoreCase(hostString)) 
			{
				LOG.debug("Host successfully removed.");
				root.removeContent(existingHost);
				existingHost.detach();
				foundHost = true;
				break;
			}
		}
		
		if(foundHost) {
			// Attempt to remove the host from the dynamic cache
			if(hostCache != null) {
				hostCache.remove(host);
			}
			
			//Write to file
			File hostCacheFile = new File(hostCacheFilename);
			XMLOutputter outputter = new XMLOutputter();
			outputter.setFormat(Format.getPrettyFormat()); 
	
			try {
				outputter.output(doc, new FileOutputStream(hostCacheFile));
				LOG.info("	-File successfully saved.");
			}
			catch(IOException e) {
				LOG.error("Could not write to " + hostCacheFilename);
				LOG.error(e.getMessage());
			}
		} else {
			LOG.error("Specified host could not be found in host cache.");
		}
		
		return foundHost;
	}
	
	
	
	
	
	/**
	 * convenience method to output the XML to a unicode string.
	 * @return
	 */
	public String toXMLString(){
		XMLOutputter outputter = new XMLOutputter();
		outputter.setFormat(Format.getPrettyFormat());
		return outputter.outputString(doc); 
		
	}

		
	/** add a host*/
	public void addHost(Host host){
		List<Element> staticHosts = root.getChildren("host");
		for(Element e : staticHosts) {
			try {
				Host existingHost = new Host(e.getAttributeValue("ip"), 
						Integer.parseInt(e.getAttributeValue("port")), 0, 0);
				if(host.equals(existingHost)) {
					// Host already exists, ignore this add request
					return;
				}
			} catch (NumberFormatException ex) {
				LOG.error("Cached host has invalid port value: " + e.getAttributeValue("port"));
				continue;
			}
		}

		Element newHostXml = new Element("host");
		newHostXml.setAttribute("ip", host.getIPAddress());
		newHostXml.setAttribute("port", Integer.toString(host.getPort()));
		root.addContent(newHostXml);
	}
	/**
	 * collects all hosts from dynamic hostcache, then saves them to file
	 */
	public void saveHostCache() {	
		//get from hc
		for (Host h: hostCache.getFriendList()){
			addHost(h);
		}
		
		//Write to file
		File hostCacheFile = new File(hostCacheFilename);
		XMLOutputter outputter = new XMLOutputter();
		outputter.setFormat(Format.getPrettyFormat()); 

		try {
			outputter.output(doc, new FileOutputStream(hostCacheFile));
			LOG.info("	-File successfully saved.");
		}
		catch(IOException e) {
			LOG.error("Could not write to " + hostCacheFilename);
			LOG.error(e.getMessage());
		}
	}
}