import java.util.Vector;

/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2016)
 *                  Anchen CHAI (2016)
 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */

public class GfalFile {
	public Vector<SE> replicas;
	private long nbreplicas;
	private long nb_current_replica;
	private LogicalFile logicalFile;
	
	public GfalFile(LogicalFile lf) {
		super();
		this.logicalFile = new LogicalFile(lf.getName(),lf.getSize(),lf.getLocations());
		this.nbreplicas= lf.getLocations().size();
		this.nb_current_replica = 0;
		this.replicas = new Vector<SE>();
	}
	
	public void NextReplica(){
		nb_current_replica = (nb_current_replica+1)%nbreplicas;
		
	}
	
	public SE getCurrentReplica(){
		
		return replicas.get((int) nb_current_replica);
	}
	
	public LogicalFile GetLogicalFile(){
		return logicalFile;
	}
	
	public long getNbreplicas() {
		return nbreplicas;
	}

	public long getNb_Current_replica() {
		return nb_current_replica;
	}
	
		
}
