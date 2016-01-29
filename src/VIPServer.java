/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS, CREATIS
 * Contributor(s) : Frédéric SUTER, Sorina CAMARASU-POP (2015)

 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import java.util.Vector;

import org.simgrid.msg.Msg;
import org.simgrid.msg.Host;
import org.simgrid.msg.Process;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.HostNotFoundException;

public class VIPServer extends Process {
	private static DefaultLFC defaultLFC = null;
	private static Vector<LFC> lfcList = new Vector<LFC>();
	private static DefaultSE defaultSE = null;
	private static Vector<SE> seList = new Vector<SE>();

	// Worker node management for registration and termination
	private Vector<Gate> gateWorkers = new Vector<Gate>();
	private Vector<Merge> mergeWorkers = new Vector<Merge>();
	private int endedGateWorkers = 0;
	private int runningMergeWorkers = 0;

	public static LFC getDefaultLFC() {
		return defaultLFC;
	}

	public static void setDefaultLFC(DefaultLFC lfc) {
		if (defaultLFC != null) {
			Msg.warn("The default LFC has already been identified. Please "
					+ "check there is only one 'DefaultLFC' process in the "
					+ "deployement file.");
		} else {
			defaultLFC = lfc;
		}
	}

	public static Vector<LFC> getLFCList() {
		return lfcList;
	}

	public static SE getDefaultSE() {
		return defaultSE;
	}

	public static void setDefaultSE(DefaultSE se) {
		if (defaultSE != null) {
			Msg.warn("The default SE has already been identified. Please "
					+ "check there is only one 'DefaultSE' process in the "
					+ "deployement file.");
		} else {
			defaultSE = se;
			Msg.info("Default SE is '" + defaultSE.getName() + "'");
		}
	}

	public static Vector<SE> getSEList() {
		return seList;
	}

	public static SE getSEbyName(String seName) {
		for (SE se : seList)
			if (se.getName().compareToIgnoreCase(seName)==0)
				return se;
		// Some worker may define a close SE that was never used, hence that
		// exists neither in the platform nor the deployment files. In that case
		// we fall back on the default SE.
		Msg.warn("Cannot find an SE named '" + seName + "'. Return Default SE");
		return VIPServer.getDefaultSE();
	}

	public VIPServer(Host host, String name, String[] args) {
		super(host, name, args);
	}

	public void main(String[] args) throws HostFailureException,
			HostNotFoundException {
		Msg.info("A new simulation starts!");
		boolean stop = false, timer = false;
		long totalParticleNumber = 0;
		Msg.verb("Write log file header"
				+ "with JobId,DownloadDuration_Sim,UploadDuration_Sim");
		System.out.println("JobId,DownloadDuration_Sim,UploadDuration_Sim");
		System.err.println("JobId,Destination,Source,FileSize,Time,UpDown");
		while (!stop) {
			// Use of some simulation magic here, every worker knows the
			// mailbox of the VIP server
			GateMessage message = (GateMessage) Message.getFrom("VIPServer");
			Job job = (Job) message.getSender();

			switch (message.getType()) {
			case "GATE_CONNECT":
				gateWorkers.add((Gate) job);

				Msg.debug(gateWorkers.size() + " GATE worker(s) registered "
						+ "out of " + VIPSimulator.numberOfGateJobs);

				job.begin();
				break;

			case "GATE_PROGRESS":
				totalParticleNumber += message.getParticleNumber();
				Msg.info(totalParticleNumber
						+ " particles have been computed. "
						+ VIPSimulator.totalParticleNumber + " are expected.");
				if (totalParticleNumber < VIPSimulator.totalParticleNumber) {
					// WARNING TEMPORARY HACK FOR FIRST TEST
					job.end();
					// SHOULD BE REPLACED BY
					// job.carryOn();
				} else {
					if (!timer) {
						Msg.info("The expected number of particles has been "
								+ "reached. Start a timer!");
						new Process(this.getHost(), "Timer") {
							public void main(String[] args)
									throws HostFailureException {
								Process.sleep(VIPSimulator.sosTime);
								if (runningMergeWorkers < VIPSimulator.numberOfMergeJobs) {
									Msg.info("Timeout has expired. Wake up "
											+ mergeWorkers.size()
											+ " Merge worker(s)");

									mergeWorkers.firstElement().begin();
									runningMergeWorkers++;
								} else {
									Msg.info("No need for Merge workers on "
											+ " timeout expiration");
								}
							}
						}.start();
						timer = true;
					}
					job.end();
				}
				break;

			case "GATE_DISCONNECT":
				// a GATE job is now complete, send it a kill signal.
				job.kill();

				endedGateWorkers++;
				if (endedGateWorkers == VIPSimulator.numberOfGateJobs) {
					// Add a safety guard in case the deployment has no Merge
					if (VIPSimulator.numberOfMergeJobs == 0)
						stop = true;
					if (runningMergeWorkers < VIPSimulator.numberOfMergeJobs) {
						Msg.info("All GATE workers sent a 'GATE_END' message"
								+ "Wake up " + mergeWorkers.size()
								+ " Merge worker(s)");
						runningMergeWorkers++;
						mergeWorkers.firstElement().begin();
					}
				}
				break;

			case "MERGE_CONNECT":
				mergeWorkers.add((Merge) job);
				Msg.debug(mergeWorkers.size() + " MERGE worker(s) registered "
						+ "out of " + VIPSimulator.numberOfMergeJobs);
				break;

			case "MERGE_DISCONNECT":
				// a MERGE job is now complete, send it a kill signal.
				job.kill();
				// then stop
				stop = true;
				break;
			default:
				break;

			}
		}

		// sleep sosTime so that tasks have the time to finish before shutting
		// down the LFCs and SEs
		Process.sleep(VIPSimulator.sosTime);

		Msg.info("Server waited for " + VIPSimulator.sosTime / 1000
				+ " seconds." + " It's time to shutdown the system.");

		// Shutting down all the LFCs
		for (LFC lfc : getLFCList())
			lfc.kill();

		// Shutting down all the SEs
		for (SE se : getSEList())
			se.kill();
	}
}
