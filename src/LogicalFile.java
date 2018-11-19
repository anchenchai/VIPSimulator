/*
 * Copyright (c) Centre de Calcul de l'IN2P3 du CNRS
 * Contributor(s) : Frédéric SUTER (2015)
 *                  Anchen CHAI (2016)
 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 */
//import java.util.Random;
import java.util.Vector;

public class LogicalFile {
	private String name;
	private long size;
	private Vector<SE> locations;
	// private Random randomGenerator;
	private int index;

	public String getName() {
		return name;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public long getSize() {
		return size;
	}

	public void addLocation(SE se) {
		locations.add(se);
	}

	public boolean isNewLocation(SE se) {
		return locations.contains((Object) se);
	}

	public SE getLocation() {
		// TODO return SE randomly for now. Might be interesting to implement some load balancing strategy
		int selectedIndex = (index++) % locations.size();
		// randomGenerator.nextInt(locations.size());
		return locations.get(selectedIndex);
	}
	
	public Vector<SE> getLocations() {
		return locations;
	}
	public String toString() {
		return "file '" + name + "' of size " + size + " stored on " + locations.toString();
	}

	@Override
	public boolean equals(Object obj) {
		LogicalFile file = (LogicalFile) obj;
		return name.equals(file.getName());
	}

	public LogicalFile(String name, long size, Vector<SE> locations) {
		super();
		this.index = 0;
		this.name = name;
		this.size = size;
		this.locations = locations;
		// this.randomGenerator = new Random();
	}

	// In most cases, this constructor with a single location will be used.
	public LogicalFile(String name, long size, SE location) {
		this(name, size, (Vector<SE>) null);
		this.locations = new Vector<SE>();
		this.locations.add(location);
	}
}
