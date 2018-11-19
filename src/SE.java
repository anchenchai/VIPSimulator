/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015-2016)
 *                  Anchen CHAI (2016)
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import org.simgrid.msg.Msg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

import org.simgrid.msg.Host;
import org.simgrid.msg.Process;

import org.simgrid.msg.MsgException;

public class SE extends GridService {
	// We add a virtual logical file catalog in each SE, which contains the logical files stored in this SE
	// from a CSV file stored in working directory that contains logical file description in the following format:
	// name,size,se_1<:se_2:...:se_n>
	// The populate function reads and parses that file, create LogicalFile objects and add them to the local catalog.
	private void populate(String csvFile) {
		Msg.info("Population of SE'" +getName()+ "' from '" + csvFile + "'");
		try {
			BufferedReader br = new BufferedReader(new FileReader(csvFile));
			String line = "";
			while ((line = br.readLine()) != null) {
				String[] fileInfo = line.split(",");
				String[] seNames = fileInfo[2].split(":");
				for (String se : seNames){
					if(se.equalsIgnoreCase(getName())){
						LogicalFile file = new LogicalFile(fileInfo[0], Long.valueOf(fileInfo[1]).longValue(), 
								VIPServer.getSEbyName(se));
						catalog.add(file);
						Msg.info("add file '" + file.toString()+"' to SE:" +getName()+"'s catalog");
					}

				}
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void sendAckTo(String mailbox) {
		SEMessage.sendTo(mailbox, "UPLOAD_ACK", "");
		Msg.debug("'SE@" + getName() + "' sent an ACK on '" + mailbox + "'");
	}

	private void sendFileTo(String destination, long size) {
		SEMessage.sendTo(destination, "FILE_TRANSFER", null, size);
	}

	public SE(Host host, String name, String[] args) {
		super(host, name, args);
		// Keep track of this process in a global list. This is used at the end
		// of the simulation to cleanly kill this process.
		VIPServer.getSEList().add(this);
		Msg.debug("Register SE on " + name);
		this.catalog = new Vector<LogicalFile>();
	}

	public void main(String[] args) throws MsgException {
		String csvFile = (args.length > 0 ? args[0] : null);
		if(csvFile != null) {
			populate(csvFile);
			Msg.debug(this.toString());
		}
		
		for (int i = 0; i < 500; i++) {
			mailboxes.add(new Process(name, name + "_" + i) {
				public void main(String[] args) throws MsgException {
					String mailbox = getName();
					Msg.debug("Create a new mailbox on: " + mailbox);

					while (true) {
						SEMessage message = (SEMessage) Message.getFrom(mailbox);

						switch (message.getType()) {
						case "DOWNLOAD_REQUEST":
							// A worker asked for a physical file. A data transfer of getSize() bytes occurs upon reply.
							// TODO This will have to be replaced/completed by some I/O operations at some point to
							// TODO increase realism.
							String fileName = message.getFileName();
							long size = getLogicalFileByName(fileName).getSize();			
							Msg.debug("SE '" + name + "' send file '" + fileName + "' of size " + size + " to '"
									+ ((Job) message.getSender()).getName() + "'");
							sendFileTo("return-" + mailbox, size);
							break;
						case "FILE_TRANSFER":
							// A physical file has been received (inducing a data transfer). An ACK is sent back to 
							// notify the reception of the file.
							// TODO This will have to be replaced/completed by some I/O operations at some point to
							// TODO increase realism.
							String fileNameUpload = message.getFileName();
							// getName returns the name of the listener process, i.e., SE name+ "_*", keep name only
							String seName = getName().split("_")[0];
							
							LogicalFile file = new LogicalFile(fileNameUpload,message.getSize(),
									VIPServer.getSEbyName(seName));
							// add uploaded file to SE's catalog
							catalog.add(file);
							sendAckTo("return-" + mailbox);
							break;
						default:
							break;
						}
					}
				}
			});
			mailboxes.lastElement().start();
		}
	}

	public void upload(String logicalFileName,long size) {
		String mailbox = this.findAvailableMailbox(2000);
		SEMessage.sendTo(mailbox, "FILE_TRANSFER", logicalFileName, size);
		Msg.info("Sent upload request of size " + size + ". Waiting for an ack");
		Message.getFrom("return-" + mailbox);
	}

	public long download(String logicalFileName) {
		String mailbox = this.findAvailableMailbox(2000);
		SEMessage.sendTo(mailbox, "DOWNLOAD_REQUEST", logicalFileName);
		Msg.info("Sent download request for '" + logicalFileName + "'. Waiting for reception ...");
		SEMessage m = (SEMessage)Message.getFrom("return-" + mailbox);
		return m.getSize();
	}

	public String toString() {
		return name;
	}

}
