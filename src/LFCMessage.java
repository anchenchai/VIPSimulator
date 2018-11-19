/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015)

 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import java.util.Vector;

import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.TimeoutException;

public class LFCMessage extends Message {
	// this class of control messages is dedicated to the interactions with the Logical File Catalog(s) that happen 
	// only through lcg-utils functions.  These functions are called by the Job processes.
	private Vector<LogicalFile> fileList = null;

	private int replicas_info = 0;
	
	public LogicalFile getFile() {
		return fileList.firstElement();
	}

	public Vector<LogicalFile> getFileList() {
		return fileList;
	}

	private LFCMessage(String type, String logicalName, Vector<LogicalFile> files) {
		super(type, 1125000000, 100);
		this.fileName = logicalName;
		this.fileList = files;
	}
	// A message to see the info of a replica in a SE
	private LFCMessage(String type, String fileName_SE, int info) {
		super(type, 1125000000, 100);
		this.fileName = fileName_SE;
		this.replicas_info = info;
	}
	
	
	public static void sendTo(String destination, String type, String logicalName, Vector<LogicalFile> fileList) {
		LFCMessage m = new LFCMessage(type, logicalName, fileList);
		try {
			Msg.debug("Send a '" + type + "' message to " + destination);
			m.send(destination);
		} catch (MsgException e) {
			Msg.error("Something went wrong when emitting a '" + type + "' message to '" + destination + "'");
			e.printStackTrace();
		}
	}
	
	public static void sendTo(String destination, String type, String fileName_SE, int info) {
		LFCMessage message = new LFCMessage(type, fileName_SE, info);
		try {
			Msg.debug("Send a '" + type + "' message to " + destination);
			message.send(destination);
		}
		catch (MsgException e) {
			Msg.error("Something went wrong when emitting a '" + type + "' message to '" + destination + "'");
			e.printStackTrace();
		}
	}
	public int getReplicas_info() {
		return replicas_info;
	}

}
