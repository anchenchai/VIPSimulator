/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015)
 *                  Anchen CHAI (2016)
 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import java.util.HashMap;
import java.util.Vector;

import org.simgrid.msg.Host;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Msg;
import org.simgrid.msg.Mutex;
import org.simgrid.msg.Process;
import org.simgrid.msg.Task;

public abstract class GridService extends Process {
	protected String name;
	protected Vector<Process> mailboxes;

	// A Logical File Catalog service is defined by:
	// hostName: the name of the host that runs the service
	// catalog: a vector of logical files
	protected Vector<LogicalFile> catalog;
	
	// A (SE_file, int) Hashmap to indicate whether the desired file exist in a SE
	// For example:	(SE1_file, 0) means file is replicating to SE1 in progress
	//				(SE1_file, 1) means file exists in SE1
	//              (SE1_file, 2) means failure to copy file in SE1 during execution
	public HashMap<String, Integer> replicas_info = new HashMap<String,Integer>();
	
	// Mutex to modify the status in HashMap replicas_info
	public HashMap<String, Mutex> transfer_locks = new HashMap<String, Mutex>();
	
	
	// Global mutex for GridService to prevent several jobs 
	// to modify transfer_locks or replicas_info at same time  
	public Mutex grid_mutex = new Mutex();
	
	
	protected String findAvailableMailbox(long retryAfter) {
		while (true) {
			for (Process listener : this.mailboxes) {
				String mailbox = listener.getName();
				if (Task.listen(mailbox)) {
					Msg.verb("Send a message to : " + mailbox + " which is listening");
					return mailbox;
				}
			}
			try {
				Msg.verb("All the listeners are busy. Wait for " + retryAfter + "ms and try again");
				Process.sleep(retryAfter);
			} catch (HostFailureException e) {
				e.printStackTrace();
			}
		}
	}

	protected LogicalFile getLogicalFileByName(String logicalFileName) {
		LogicalFile file = catalog.get(catalog.indexOf((Object)new LogicalFile(logicalFileName, 0, new Vector<SE>())));
		
		if (file == null) {
			Msg.error("File '" + logicalFileName + "' is stored on no SE. Exiting with status 1");
			System.exit(1);
		}
		return file;
		
	}
	
	public String getName() {
		return name;
	}

	public GridService(Host host, String name, String[] args) {
		super(host, name, args);
		this.name = getHost().getName();
		this.mailboxes = new Vector<Process>();
	}

	public void kill() {
		for (Process p : mailboxes)
			p.kill();
	}
}
