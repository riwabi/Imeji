/**
 * License: src/main/resources/license/escidoc.license
 */

package de.mpg.jena.util;

import thewebsemantic.Id;

public class Counter {

	private int id = 0;
	
	private int counter;

	
	public void setId(int id) {
		this.id = id;
	}

	@Id
	public int getId() {
		return id;
	}

	public void setCounter(int counter) {
		this.counter = counter;
	}

	public int getCounter() {
		return counter;
	}
	
	
	
	
}
