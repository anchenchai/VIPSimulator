/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015)
 *                  Anchen CHAI (2016)

 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Msg;

import java.util.Vector;

import org.simgrid.msg.Host;
import org.simgrid.msg.Process;
import org.simgrid.msg.MsgException;

public class Gate extends Job {
	private long simulateForNsec(long nSec) throws HostFailureException {
		double nbPart;

		Process.sleep(nSec);
		nbPart = VIPSimulator.eventsPerSec * nSec;
		Msg.info("simulateForNsec: '" + getName() + "' simulated " + (long) nbPart + " particles");
		// WARNING TEMPORARY HACK FOR FIRST TEST
		return 1;
		// SHOULD BE REPLACED BY
		// return (long) (nbPart);
	}

	private void connect() {
		// Use of some simulation magic here, every worker knows the mailbox of the VIP server
		GateMessage.sendTo("VIPServer", "GATE_CONNECT", 0);
	}

	private void sendProgress(long simulatedParticles) {
		// Use of some simulation magic here, every worker knows the mailbox of the VIP server
		GateMessage.sendTo("VIPServer", "GATE_PROGRESS", simulatedParticles);
	}

	private void disconnect() {
		// Use of some simulation magic here, every worker knows the mailbox of the VIP server
		GateMessage.sendTo("VIPServer", "GATE_DISCONNECT", 0);
	}

	public Gate(Host host, String name, String[] args) {
		super(host, name, args);
	}

	public void main(String[] args) throws MsgException {
		// TODO have to set the name here, might be a bug in simgrid
		setName();
		long nbParticles = 0;
		long simulatedParticles = 0;
		long uploadFileSize = 0;
		String transferInfo;
		Vector<SE> actualSources = new Vector<SE>();

		int jobId = (args.length > 0 ? Integer.valueOf(args[0]).intValue() : 1);
		long executionTime = (args.length > 1 ? 1000 * Long.valueOf(args[1]).longValue() : VIPSimulator.sosTime);
		if (VIPSimulator.version == 1) {
			uploadFileSize = VIPSimulator.fixedFileSize;
		} else {
			uploadFileSize = (args.length > 2 ? Long.valueOf(args[2]).longValue() : 1000000);
			if (VIPSimulator.version == 3){
				actualSources.add(VIPServer.getSEbyName(args[3]));
				actualSources.add(VIPServer.getSEbyName(args[4]));
				actualSources.add(VIPServer.getSEbyName(args[5]));
			}
		}
		Msg.info("Register GATE on '" + getName() + "'");
		this.connect();

		while (true) {
			GateMessage message = (GateMessage) Message.getFrom(getName());

			switch (message.getType()) {
			case "BEGIN":
				Msg.info("Processing GATE");
				if (VIPSimulator.version == 1){
					// The first version of the GATE simulator does a single download whose size was given as input
					downloadTime.start();
					transferInfo = LCG.cp("input.tgz", "/scratch/input.tgz", VIPServer.getDefaultLFC());
					logDownload(jobId, transferInfo, 0, "gate");
					downloadTime.stop();
				} else {
					// upload-test
					// TODO to be factored at some point
					uploadTestTime.start();
					LCG.cr("output-0.tar.gz-uploadTest", 12, "output-0.tar.gz-uploadTest", getCloseSE(),
							VIPServer.getDefaultLFC());
					uploadTestTime.stop();
					System.err.println(jobId + "," + getHost().getName() +  "," + getCloseSE() + ",12,"
							+ uploadTestTime.getValue() + ",gate,0");

					// Downloading inputs:
					//   1) wrapper script
					//   2) Gate release
					//   3) workflow specific parameters
					// If less than 3 files were found in the catalog, exit.
					downloadTime.start();
					if (VIPSimulator.gateInputFileNames.size() < 3){
						Msg.error("Some input files are missing. Exit!");
						System.exit(1);
					}
					Vector<SE> replicaLocations;

					for (String logicalFileName: VIPSimulator.gateInputFileNames){
						Timer lrDuration = new Timer();
						//Gate job first do lcg-lr to check whether input file exists in closeSE
						lrDuration.start();
						replicaLocations = LCG.lr(VIPServer.getDefaultLFC(),logicalFileName);
						lrDuration.stop();

						if (VIPSimulator.version == 2){
							// if closeSE found, lcg-cp with closeSE, otherwise normal lcg-cp
//							if(replicaLocations.contains(getCloseSE())) 
//								transferInfo = LCG.cp(logicalFileName, 
//										"/scratch/"+logicalFileName.substring(logicalFileName.lastIndexOf("/")+1),
//										getCloseSE());
//							else
//								transferInfo = LCG.cp(logicalFileName,
//										"/scratch/"+logicalFileName.substring(logicalFileName.lastIndexOf("/")+1),
//										VIPServer.getDefaultLFC());

							transferInfo = LCG.cp1(logicalFileName,
									"/scratch/"+logicalFileName.substring(logicalFileName.lastIndexOf("/")+1),
									VIPServer.getDefaultLFC());
							
						} else {
							transferInfo = LCG.cp(logicalFileName, 
									"/scratch/"+logicalFileName.substring(logicalFileName.lastIndexOf("/")+1),
									(SE) actualSources.remove(0));
						}

						logDownload(jobId, transferInfo, lrDuration.getValue(), "gate");
					}
					downloadTime.stop();
				}
			case "CARRY_ON":
				// Compute for sosTime seconds
				computeTime.start();

				// TODO Discuss what we can do here. Make the process just sleep for now
				simulatedParticles = simulateForNsec(executionTime);

				computeTime.stop();

				nbParticles += simulatedParticles;

				// if dynamic, Gate send Progress to VIPServer; otherwise, enter "END" directly 
				if(VIPSimulator.workflowVersion.equals("dynamic")){
					Msg.info("Sending computed number of particles to 'VIPServer'");
					sendProgress(simulatedParticles);
					break;
				}

			case "END":
				Msg.info("Stopping Gate job and uploading results. " + nbParticles + 
						" particles have been simulated by '" + getName() + "'");

				// The size of the file to upload is retrieve from the logs
				String logicalFileName = "results/" + Long.toString(nbParticles) + "-partial-" + getName() + "-" 
						+ Double.toString(Msg.getClock()) + ".tgz";

				uploadTime.start();
				LCG.cr("local_file.tgz", uploadFileSize, logicalFileName, getCloseSE(), VIPServer.getDefaultLFC());
				uploadTime.stop();
				System.err.println(jobId + "," + getHost().getName() + "," + getCloseSE() + "," + uploadFileSize +","
						+ uploadTime.getValue() + ",gate,1");

				Msg.info("Disconnecting GATE job. Inform VIP server.");
				this.disconnect();

				Msg.info("Spent " + downloadTime.getValue() + "s downloading, " + computeTime.getValue() 
						+ "s computing, and " + uploadTime.getValue() + "s uploading.");
				System.out.println(jobId + "," + downloadTime.getValue() + "," + uploadTime.getValue());
				break;
			default:
				break;
			}
		}
	}
}
