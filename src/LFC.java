/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015)
 *                  Anchen CHAI (2016)
 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

import org.simgrid.msg.Msg;
import org.simgrid.msg.Host;
import org.simgrid.msg.Process;
import org.simgrid.msg.MsgException;

public class LFC extends GridService {
	// A simulation can begin with some logical files referenced in the LFC.
	// In that case, the LFC process is launched with an argument which is the name of a CSV file stored in working 
	// directory that contains logical file description in the following format:
	// name,size,se_1<:se_2:...:se_n>
	// The populate function reads and parses that file, create LogicalFile objects and add them to the local catalog.
	private void populate(String csvFile) {
		Msg.info("Population of LFC '" + name + "' from '" + csvFile + "'");

		try {
			BufferedReader br = new BufferedReader(new FileReader(csvFile));
			String line = "";

			while ((line = br.readLine()) != null) {
				String[] fileInfo = line.split(",");
				String[] seNames = fileInfo[2].split(":");
				Vector<SE> locations = new Vector<SE>();
				for (String seName : seNames){
					SE se = VIPServer.getSEbyName(seName);
					if (!se.getName().equals(seName)){
						Msg.warn("'"+ seName + "' is not a valid SE name. A fallback to '" +VIPServer.getDefaultSE()
								+ "' occured");
					} else {
						locations.add(se);
					}
				}
				LogicalFile file = new LogicalFile(fileInfo[0], Long.valueOf(fileInfo[1]).longValue(), locations);
				Msg.info("Importing file '" + file.toString());				
				catalog.add(file);
				// also populate the global vectors that list the names of GATE and Merge input files
				if (fileInfo[0].startsWith("inputs/gate/")){
					Msg.verb("'"+fileInfo[0] +"' added as Gate input");
					VIPSimulator.gateInputFileNames.add(fileInfo[0]);
				} else {
					if (fileInfo[0].startsWith("inputs/merge/")){
						Msg.verb("'"+fileInfo[0] +"' added as Merge input");
						VIPSimulator.mergeInputFileNames.add(fileInfo[0]);
					}
				}
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// A worker might want to register a new logical file, or a replica on an existing file in the catalog. This 
	// function first checks if a file with the same name already exists. If it does, it determines whether it is a
	// new replica or not. Otherwise, it creates a new entry in the catalog for that file.
	private void addToCatalog(LogicalFile newFile) {
		Msg.debug("Inserting '" + newFile.toString() + "'into the catalog");
		if (catalog.contains((Object) newFile)) {
			LogicalFile file = catalog.get(catalog.indexOf(newFile));
			if (!file.isNewLocation(newFile.getLocation())) {
				// This has to be a new replica
				Msg.debug("New replica for '" + newFile.getName() + "' on '" + newFile.getLocation() + "'");
				file.addLocation(newFile.getLocation());
			} else {
				Msg.debug(file.toString() + "is already registered");
			}
		} else {
			// This file is not registered yet, create and add it
			Msg.debug("'" + newFile.getName() + "' is not registered yet");
			catalog.add(newFile);
		}
		Msg.debug("LFC '" + name + "' registered " + newFile.toString());
	}

	private LogicalFile getReplicaByName(String logicalFileName) {
		LogicalFile replica = new LogicalFile(logicalFileName, 0, new Vector<SE>());

		LogicalFile file = catalog.get(catalog.indexOf((Object) replica));
		if (file == null) {
			Msg.error("File '" + logicalFileName + "' is stored on no SE. Exiting with status 1");
			System.exit(1);
		}
		replica.setSize(file.getSize());
		replica.addLocation(file.getLocation());

		return replica;
	}
	
	private void sendAckTo(String mailbox) {
		LFCMessage.sendTo(mailbox, "REGISTER_ACK", null, null);
		Msg.debug("'LFC@" + getName() + "' sent an ACK on '" + mailbox + "'");
	}

	private void sendLogicalFile(String mailbox, LogicalFile file) {
		Vector<LogicalFile> list = new Vector<LogicalFile>();
		list.add(file);
		LFCMessage.sendTo(mailbox, "SEND_LOGICAL_FILE", null, list);
		Msg.debug("'LFC@" + name + "' sent logical " + file.toString() + " back on '" + mailbox + "'");
	}

	private void sendLogicalFileList(String mailbox, Vector<LogicalFile> list) {
		LFCMessage.sendTo(mailbox, "SEND_LOGICAL_FILE", null, list);
	}
	public LFC(Host host, String name, String[] args) {
		super(host, name, args);
		this.catalog = new Vector<LogicalFile>();
		Msg.debug("Register LFC on " + name);
		VIPServer.getLFCList().add(this);
	}

	public void main(String[] args) throws MsgException {
		// If this LFC process is started with an argument, we populate the catalog from the CSV file given as args[0]
		String csvFile = (args.length > 0 ? args[0] : null);

		if ((VIPSimulator.version != 1) && (csvFile != null)) {
			populate(csvFile);
			Msg.debug(this.toString());
		} else {
			LogicalFile file = new LogicalFile("input.tgz", VIPSimulator.fixedFileSize, VIPServer.getDefaultSE());
			Msg.info("Importing file '" + file.toString());
			catalog.add(file);
		}

		for (int i = 0; i < 500; i++) {
			mailboxes.add(new Process(name, name + "_" + i) {
				public void main(String[] args) throws MsgException {
					String mailbox = getName();

					Msg.debug("Create a new mailbox on: " + mailbox);
					while (true) {
						LFCMessage message = (LFCMessage) Message.getFrom(mailbox);

						switch (message.getType()) {
						case "REGISTER_FILE":
							// Add an entry in the catalog for the received logical file, if needed.
							addToCatalog(message.getFile());
							// Then send back an ACK to the the sender.
							sendAckTo("return-" + mailbox);
							break;
						case "ASK_LOGICAL_FILE":
							LogicalFile file = getReplicaByName(message.getFileName());
							// Send this file back to the sender
							sendLogicalFile("return-" + mailbox, file);
							break;
						case "ASK_LS":
							Vector<LogicalFile> directoryContents = new Vector<LogicalFile>();
							for (LogicalFile f : catalog)
								if (f.getName().matches(message.getFileName() + "(.*)"))
									directoryContents.add(f);
							// Send the directory contents back to the sender
							sendLogicalFileList("return-" + mailbox, directoryContents);
							break;
						case "ASK_LR":	
							LogicalFile fileLr = getLogicalFileByName(message.getFileName());
							sendLogicalFile("return-" + mailbox, fileLr);
						default:
							break;
						}
					}
				}
			});
			mailboxes.lastElement().start();
		}
	}

	public void register(LogicalFile file) {
		String mailbox = this.findAvailableMailbox(100);
		Vector<LogicalFile> list = new Vector<LogicalFile>();
		list.add(file);
		LFCMessage.sendTo(mailbox, "REGISTER_FILE", null, list);
		Message.getFrom("return-" + mailbox);
	}

	public LogicalFile getLogicalFile(String logicalFileName) {
		String mailbox = this.findAvailableMailbox(100);
		LFCMessage.sendTo(mailbox, "ASK_LOGICAL_FILE", logicalFileName, null);
		Msg.info("Asked about '" + logicalFileName + "'. Waiting for information ...");

		LFCMessage m = (LFCMessage) Message.getFrom("return-" + mailbox);
		return m.getFile();
	}

	public Vector<LogicalFile> getLogicalDirectoryContents(String directoryName) {
		String mailbox = this.findAvailableMailbox(100);
		LFCMessage.sendTo(mailbox, "ASK_LS", directoryName, null);
		Msg.info("Asked for list of files to merge in '" + directoryName + "'. Waiting for reply ...");
		LFCMessage m = (LFCMessage) Message.getFrom("return-" + mailbox);
		return m.getFileList();
	}
	
	public Vector<SE> getReplicaLocations(String logicalFileName){
		String mailbox = this.findAvailableMailbox(100);
		LFCMessage.sendTo(mailbox, "ASK_LR", logicalFileName, null);
		Msg.info("Asked for list of replicas of logicalFileName '" + logicalFileName + "'. Waiting for reply ...");
		LFCMessage m = (LFCMessage) Message.getFrom("return-" + mailbox);
		return m.getFile().getLocations();
	}

	public String toString() {
		return catalog.toString();
	}
	
	// Put SURLs in order of preference:
	//   1. SURLs from default SE
	//   2. SURLs from local domain
	//   3. Others
	public void fillsurls(GfalFile gf){
		
		@SuppressWarnings("unchecked")
		Vector<SE> fileReplicas = (Vector<SE>) gf.GetLogicalFile().getLocations().clone();
		Job job = (Job) getCurrentProcess();
		String JobName = job.getHost().getName();
		SE CloseSE = job.getCloseSE();
		String HostName = JobName.split("\\.")[0];
		String DomainName = JobName.substring(HostName.length()+1, JobName.length());
		Msg.info("Construct sorted list of replicas for "+ job.getName());
		
		// firstly add CloseSE if there exists
		if(fileReplicas.contains(CloseSE)){
			gf.replicas.addElement(CloseSE);
			fileReplicas.remove(CloseSE);
		}
		// add replicas with same domain name
		for(SE se:gf.GetLogicalFile().getLocations()){
			if(se.getName().contains(DomainName)){
				gf.replicas.addElement(se);
				fileReplicas.remove(se);
			}
		}
		// For the rest, add randomly in the list
		int NbReplicasLeft = fileReplicas.size();
		int randomInt;
		for(int i=0; i < NbReplicasLeft; i++){
			Random randomGenerator = new Random();
			randomInt = randomGenerator.nextInt(NbReplicasLeft -i);
			gf.replicas.addElement(fileReplicas.get(randomInt));
			
			fileReplicas.remove(randomInt);
		}	
		
		if(fileReplicas.size()>0 || gf.replicas.size() != gf.getNbreplicas()) 
			Msg.info("Something went wrong, when constructing the sorted replicas vector of " 
					 + gf.GetLogicalFile().getName()+" for Job"+JobName);

	}
	
}