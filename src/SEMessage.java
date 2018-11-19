/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015)

 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.TimeoutException;

public class SEMessage extends Message {

	private long size = 0;

	public long getSize() {
		return size;
	}
	
	/**
	 * Constructor, builds a new FILE_TRANSFER message
	 */
	private SEMessage(String type, String fileName, long size) {
		super(type, 1125000000, size);
		this.fileName = fileName;
		this.size = size;
	}
	
	/**
	 * Constructor, builds a new control (DOWNLOAD_REQUEST/UPLOAD_ACK) message
	 * 
	 */
	private SEMessage(String type, String fileName) {
		super(type, 1125000000, 100);
		this.fileName = fileName;
	}

	public static void sendTo(String mailbox, String type, String fileName, long size) {
		SEMessage message = new SEMessage(type, fileName, size);
		try {
			Msg.debug("Send a '" + type + "' message to " + mailbox);
			message.send(mailbox);
		} catch(TimeoutException e){
			//e.printStackTrace();
			Msg.info("Timeout exception when emitting a '" + type + "' message to '" + mailbox + "'");
		 }
		catch (MsgException e) {
			Msg.error("Something went wrong when emitting a '" + type + "' message to '" + mailbox + "'");
			e.printStackTrace();
		}
	}

	public static void sendTo(String mailbox, String type, String fileName) {
		SEMessage message =new SEMessage(type, fileName);
		try {
			Msg.debug("Send a '" + type + "' message to " + mailbox);
			message.send(mailbox);
		} catch (MsgException e) {
			Msg.error("Something went wrong when emitting a '" + type + "' message to '" + mailbox + "'");
			e.printStackTrace();
		}
	}

}
