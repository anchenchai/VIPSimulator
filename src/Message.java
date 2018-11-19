/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015)

 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Task;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.TaskCancelledException;

public abstract class Message extends Task {
	protected String fileName = null;

	public String getType() {
		return getName();
	}

	public String getFileName() {
		return fileName;
	}

	public static Message getFrom(String mailbox) {
		Message message = null;
		try {
			message = (Message) Task.receive(mailbox);
			Msg.debug("Received a '" + message.getType() + "' message from " + mailbox);
			// Simulate the cost of the local processing of the request.
			// Depends on the value set when the Message was created
			message.execute();
		} catch (MsgException e) {
			e.printStackTrace();
		}

		return message;
	}

	protected Message(String name, double flopAmount, double byteAmount) {
		super(name, flopAmount, byteAmount);
	}

	public void execute() throws HostFailureException, TaskCancelledException {
		super.execute();
	}

}
