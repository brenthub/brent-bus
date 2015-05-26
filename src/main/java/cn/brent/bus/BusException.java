package cn.brent.bus;

public class BusException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8005794291169891114L;
	
	public BusException(String message) {
		super(message);
	}
	
	public BusException() {
		super();
	}

	public BusException(String message, Throwable cause) {
		super(message, cause);
	}

	public BusException(Throwable cause) {
		super(cause);
	}
}
