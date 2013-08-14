package rti.util;

public abstract class Status {

	protected static final int WORKING = 1;
	protected static final int SLEEPING = 0;
	protected static final int STOP = -1;
    
	private int status = SLEEPING;

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}
	
	public void stop() {
		this.setStatus(STOP);
	}
}
