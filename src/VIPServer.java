import java.util.Vector;

import org.simgrid.msg.Msg;
import org.simgrid.msg.Host;
import org.simgrid.msg.Process;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.HostNotFoundException;

public class VIPServer extends Job {

	// Worker node management for registration and termination 
	private Vector<String> gateWorkers = new Vector<String>();
	private Vector<Merge> mergeWorkers = new Vector<Merge>();
	private int endedGateWorkers = 0;
	private int runningMergeWorkers = 0;

	private long totalParticleNumber = 0;

	public VIPServer(Host host, String name, String[]args) {
		super(host,name,args);
		
	}

	public void main(String[] args) throws HostFailureException, 
		HostNotFoundException {
		Msg.info("A new simulation starts!");
		boolean stop=false, timer = false;
		// Build the mailbox name from the PID and the host name. This might be 
		// useful to distinguish different Gate processes running on a same host
		setMailbox();

		// TODO what is below is very specific to GATE
		// Added to temporarily improve the realism of the simulation
		// Have to be generalized at some point.
		// WARNING: From log inspection, it seems that workers do not all get 
		// the input files from the default SE.
		LCG.crInput(VIPSimulator.getDefaultLFC(),
				"inputs/gate.sh.tar.gz", 73043,
				VIPSimulator.getDefaultSE());
		LCG.crInput(VIPSimulator.getDefaultLFC(),
				"inputs/opengate_version_7.0.tar.gz", 376927945,
				VIPSimulator.getDefaultSE());
		LCG.crInput(VIPSimulator.getDefaultLFC(),
				"inputs/file-14539084101429.zip", 514388,
				VIPSimulator.getDefaultSE());

		while(!stop){
			// Use of some simulation magic here, every worker knows the 
			// mailbox of the VIP server
			GateMessage message = getFrom("VIPServer");

			switch (message.getType()){
			case GATE_CONNECT:
				gateWorkers.add(message.getSenderName());

				Msg.debug(gateWorkers.size() +
						" GATE worker(s) registered out of " +
						VIPSimulator.numberOfGateJobs);

				Job.start(message.getSenderName());
				break;
			case MERGE_CONNECT:
				mergeWorkers.add((Merge) message.getSender());
				
				Msg.debug(mergeWorkers.size() +
						" MERGE worker(s) registered out of " +
						VIPSimulator.numberOfMergeJobs);
				break;
			case GATE_PROGRESS:
				totalParticleNumber += message.getParticleNumber();
				Msg.info (totalParticleNumber +
						" particles have been computed. "+
						VIPSimulator.totalParticleNumber + " are expected.");
				if (totalParticleNumber < VIPSimulator.totalParticleNumber){
					Job.carryOn(message.getSenderName());
				} else {
					if (!timer){
						Msg.info("The expected number of particles has been "+
								"reached. Start a timer!");
						new Process(this.getHost(),"Timer"){
							public void main(String[] args) throws 
								HostFailureException {
								Process.sleep(VIPSimulator.sosTime);
								if (runningMergeWorkers < 
										VIPSimulator.numberOfMergeJobs){
									Msg.info("Timeout has expired. Wake up " + 
										mergeWorkers.size() + 
										" Merge worker(s)");

									Job.start(mergeWorkers.firstElement().
											getMailbox());
									runningMergeWorkers++;
								} else {
									Msg.info("No need for Merge workers on " +
											" timeout expiration");
								}
							}
						}.start();
						timer = true;
					}

					Job.stop(message.getSenderName());
				}
				break;
			case GATE_DISCONNECT:
				endedGateWorkers++;
				if (endedGateWorkers == VIPSimulator.numberOfGateJobs){
					if (runningMergeWorkers < VIPSimulator.numberOfMergeJobs){
						Msg.info("All GATE workers sent a 'GATE_END' message" +
								"Wake up " + mergeWorkers.size() + 
								" Merge worker(s)");
						runningMergeWorkers++;
						Job.start(mergeWorkers.firstElement().getMailbox());
					}
					// then stop
					stop=true;
				}
				break;
			default:
				break;

			}
		}


		// sleep sosTime so that tasks have the time to finish before shutting 
		// down the LFCs and SEs
		Process.sleep(VIPSimulator.sosTime);

		Msg.info("Server waited for " + VIPSimulator.sosTime/1000 +" seconds." +
				" It's time to shutdown the system.");

		// Shutting down all the LFCs
		for (LFC lfc : VIPSimulator.getLFCList())
			lfc.kill();

		// Shutting down all the SEs
		for (SE se : VIPSimulator.getSEList())
			se.kill();
	}
}
