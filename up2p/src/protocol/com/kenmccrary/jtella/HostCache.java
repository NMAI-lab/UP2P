/*
 * Copyright (C) 2000-2001  Ken McCrary
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Email: jkmccrary@yahoo.com
 */
package protocol.com.kenmccrary.jtella;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Enumeration;
import java.util.List;
import java.util.LinkedList;
import java.util.Random;

import org.apache.log4j.Logger;

import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader.Array;

import protocol.com.kenmccrary.jtella.Host.HostState;
import protocol.com.kenmccrary.jtella.util.Log;

/**
 * EDITED BY: Daniel Meyers, 2003<br>
 * A cache of the known hosts on the network
 * 
 *How this works:
 * - There is a "soft" limit size (30 hosts): up to this size, hosts found in PONG messages will be added to the hostcache.
 * - Any nodes manually added by the user are in the hostcache
 * - The user can manually add an unlimited number of hosts
 * - If the hostcache gets bigger than the  "soft limit", and there are some "dynamically added" hosts in the hostcache, then the dynamically added ones are removed to make space for manually added ones.
 *
 */
public class HostCache {
	private static int MAX_CACHED_HOSTS = 30; // 30 hosts to match number of available connections

	private int nextHostIndex;
	private List<Host> hosts; 
	private Set<String> blacklist; //list of gnutella-id blacklisted hosts
	private boolean removingAllHosts = false;
	/** Name of Logger used by this class. */
    public static final String LOGGER = "protocol.com.kenmccrary.jtella";

    /** Logger used by this class. */
    private static Logger LOG = Logger.getLogger(LOGGER);

	/**
	 * Constructs an empty HostCache
	 *
	 */
	HostCache() {
		hosts = new ArrayList<Host>();
		blacklist=new TreeSet<String>();
		nextHostIndex = 0;
	}
	
	// Add to beginning, remove from beginning (get newest all the time)

	/**
	 * EDITED BY: Daniel Meyers, 2003
	 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
	 * Adds a host to the cache, or sets the host's state to active
	 * if it already exists in the cache (does not change the state of the
	 * passed parameter)
	 *
	 * @param host Host object representing the host to add
	 */
	public synchronized void addHost(Host host) {
		boolean hostAdded = false;
		
		if (!removingAllHosts) {
			if (!contains(host)) {
				if (hosts.size() < MAX_CACHED_HOSTS) {
					hosts.add(host);
					hostAdded = true;
					LOG.info("Adding host: " + host.toString());
				}

				else if ((hosts.size() >= MAX_CACHED_HOSTS) && host.getManual()) {
					// Iterate thought hosts and if you find one that is not manually added, 
					
					Iterator<Host> iter = hosts.iterator(); 
					
					while (iter.hasNext()) {
						Host thehost =iter.next(); 
						if (!thehost.getManual()) {
							remove(thehost);
							hosts.add(host);
							LOG.info("adding host " + host.getIPAddress() + ":" + host.getPort()
									+ "as replacement of:"
									+ thehost.getIPAddress() + ":" + thehost.getPort());
							hostAdded = true;
							break;
						}
					}
					if( !hostAdded){ // if we haven't found another host to remove: just add it, overriding soft limit.
						hosts.add(host); 
					}
				}
			} else {
				// The specified host already exists in the cache, set its state to active, and set its "manually added" status
				LOG.debug("HostCache: host already known: "+ host.getIPAddress() + ":" + host.getPort());
				if(host.getHostState() == HostState.ACTIVE) {
					LOG.debug("HostCache: Setting host state to ACTIVE, and clearing failed connection count.");
					Iterator<Host> iter = hosts.iterator();
					while (iter.hasNext()){
						Host existingHost = iter.next();
						if (existingHost.equals(host)) {
							existingHost.setHostState(HostState.ACTIVE);
							existingHost.setFailedAttempts(0);
							if (host.getManual())
								existingHost.setManual(true); // if the host is to be added manually, set the state of existing host to "manually added"
							return;
						}
					}
				}
			}
		}
	}

	/**
	 * ADDED BY: Daniel Meyers, 2003
	 * <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
	 * Adds a host to the Hostcache by IP address and port
	 * 
	 * @param ipAddress A string representation of the IP address of the host to add
	 * @param port The port that the host accepts incoming connections on
	 */
	public synchronized void addHost(String ipAddress, int port) {
		Host host = new Host(ipAddress, port, 0, 0);
		addHost(host);
	}

	/**
	 *  Removes a host from the cache
	 *
	 *  @param ipAddress address of host to remove
	 *  @param port port of host to remove
	 * /
	public synchronized void removeHost(String ipAddress, int port) {
		remove(new Host(ipAddress, port, 0, 0));
	}*/

	/**
	 *  Removes a host from the cache
	 *
	 *  @param host host to remove
	 */
	public synchronized void remove(Host host) {
		hosts.remove(host);
	}

	/**
	 *  Removes all host from the cache, if all hosts are replying as busy
	 *  after time limit X it might be an idea to clear the cache and 
	 *  repopulate from GWebCaches instead of X-Try-Ultrapeers
	 */
	public synchronized void removeAllHosts() {
		removingAllHosts = true;
		while (!hosts.isEmpty()) {
			hosts.remove(0);
		}
		removingAllHosts = false;
	}

	/**
	 *  Get a list of the Hosts cached
	 *
	 *  @return host list
	 */
	public List<Host> getKnownHosts() {
		synchronized(hosts){
		return new LinkedList<Host>(hosts);
		}
	}

	/**
	 *  Remove a host from the cache, probably because its not responding
	 *
	 */
	public void removeHost(Host host) {
		synchronized(hosts){
		hosts.remove(host);
		}
	}

	/**
	 * Return the maximum number of hosts to have cached
	 *
	 */
	public int getMaxHosts() {
		return MAX_CACHED_HOSTS;
	}

	/**
	 *  Query how many hosts are cached
	 *  
	 *  @return number of hosts
	 */
	public int size() {
		synchronized(hosts){
		return hosts.size();
		}
	}
	
	/**
	 * @return	True if the host cache contains one or more hosts with an
	 * 			inactive state (i.e anything other than HostState.ACTIVE)
	 */
	public boolean hasInactiveHost() {
		synchronized(hosts){
		Iterator<Host> iter = hosts.iterator();
		while (iter.hasNext()){
			Host host = iter.next();
			if (host.getHostState() != HostState.ACTIVE) {
				return true;
			}
		}
		}
		return false;
	}

	/**
	 *  Get the next host available
	 *
	 *  @return host or null if none available
	 */
	Host nextHost() {
		if (size() == 0) {
			return null;
		}

		if(nextHostIndex > size() - 1) {
			nextHostIndex = 0;
		}
		synchronized(hosts){
		Host returnHost = hosts.get(nextHostIndex);
		
		nextHostIndex++;
		return returnHost;
		}
	}

	/**
	 *  Get an iterator of the hosts cached
	 *
	 */
	Iterator<Host> getHosts() {
		return hosts.iterator();// .elements();
	}
	
	/**
	 * Checks if the specified host is present in the Vector
	 * 
	 * @param host the host to check for the presence of
	 * 
	 */
	public boolean  contains(Host host) {
		synchronized(hosts){
		Iterator<Host> iter = hosts.iterator();
		while (iter.hasNext()){
			Host existingHost = iter.next();
			if (existingHost.getIPAddress().equals(host.getIPAddress())
					&& existingHost.getPort() == host.getPort()) {
				return true;
			}
		}
		}
		return false;
	}

	/**
	 *  Retrieve a random sample of known hosts. The returned sample may be equal
	 *  to or smaller than the requested size
	 *
	 *  @param sampleSize desired sample size
	 *  @return sample
	 */
	public Host[] getRandomSample(int sampleSize) {
		synchronized(hosts){
			if(hosts.size()<=sampleSize){ //awkwardly copy over the list to an array
				Host[] toreturn = new Host[hosts.size()];
				for (int i=0; i<hosts.size();i++){
					toreturn[i]=hosts.get(i);
				}
				return toreturn;
			}
			else{
				
				Collections.shuffle(hosts); //shuffle so that the first "samplesize" are random
				Host[] toreturn = new Host[sampleSize];
				for (int i=0; i<hosts.size();i++){
					toreturn[i]=hosts.get(i);
				}
				return toreturn;
			}
			}
	}

	public synchronized void blacklistPeer(String guid){	
		blacklist.add(guid);	
	}
	
	public synchronized void unBlacklist(String guid){
		blacklist.remove(guid);
	}
	
	public synchronized boolean isBlacklisted(String remoteServentId) {		
		return blacklist.contains(remoteServentId);
	}
	
	/**
	 * 
	 * @return a *copy* of the black list
	 */
	public synchronized List<String> getBlackList(){
		List<String> toreturn = new LinkedList<String>();
		toreturn.addAll(blacklist);
		return toreturn;
	}

	public synchronized List<Host> getFriendList() {
		
		return null;
	}
}
