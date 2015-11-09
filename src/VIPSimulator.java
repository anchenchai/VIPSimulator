/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015)

 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
import org.simgrid.msg.Msg;

public class VIPSimulator {
	public static long totalParticleNumber;
	public static int numberOfGateJobs;
	public static String gateInputFile;
	public static long sosTime;
	public static int numberOfMergeJobs;
	public static int cpuMergeTime;
	public static double eventsPerSec;

	public static void main(String[] args) {
		Msg.init(args);
		String platform_file = null;
		String deployment_file = null;

		if (args.length < 2) {
			Msg.error("This simulator requires at least a platform and a "
					+ "deployment files tu run");
			System.exit(1);
		} else {
			platform_file = args[0];
			deployment_file = args[1];
			Msg.info("SCENARIO: platform is " + platform_file
					+ "', deployment is '" + deployment_file + "'");
		}

		totalParticleNumber = args.length > 2 ? Long.valueOf(args[2])
				.longValue() : 1000000;
		numberOfGateJobs = args.length > 3 ? Integer.valueOf(args[3])
				.intValue() : 5;
		gateInputFile = args.length > 4 ? args[4] : "file.zip";
		// SOS time is given in seconds on command line, but sleeps take values
		// in milliseconds.
		sosTime = 1000 * (args.length > 5 ? Long.valueOf(args[5]).longValue()
				: 300);
		numberOfMergeJobs = args.length > 6 ? Integer.valueOf(args[6])
				.intValue() : 1;
		cpuMergeTime = args.length > 7 ? Integer.valueOf(args[7]).intValue()
				: 10;
		eventsPerSec = args.length > 8 ? Double.valueOf(args[8]).doubleValue()
				: 200;

		Msg.info("PARAMS:   sostime is " + sosTime
				+ ", number of Gate tasks is " + numberOfGateJobs
				+ ", number of merge tasks is " + numberOfMergeJobs
				+ ", cpu merge time is " + cpuMergeTime);

		// Load the platform description
		Msg.createEnvironment(platform_file);
		// and deploy the application
		Msg.deployApplication(deployment_file);

		// Now, execute the simulation.
		Msg.run();
	}
}
