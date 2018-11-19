/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015)

 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;

public class GateMessage extends Message {
	private long particleNumber;

	public long getParticleNumber() {
		return particleNumber;
	}

	private GateMessage(String type, long particleNumber) {
		super(type, 1, 100);
		this.particleNumber = particleNumber;
	}

	public static void sendTo(String destination, String type,
			long particleNumber) {
		GateMessage m = new GateMessage(type, particleNumber);
		try {
			Msg.debug("Sending a '" + type + "' message to '" + destination + "'");
			m.send(destination);
		} catch (MsgException e) {
			Msg.error("Something went wrong when emitting a '"+ type.toString() + "' message to '" + destination + "'");
			e.printStackTrace();
		}
	}
}
