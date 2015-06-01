package cn.brent.bus.server;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.zeromq.ZMsg;

public class ServiceInfo {
	private String name; // Service name
	private ConcurrentLinkedQueue<ZMsg> requests; // List of client requests
	private ConcurrentLinkedQueue<WorkerInfo> workers; // List of waiting workers
	private long serve_at;
	private long mq_size;
	private String token; // Token authorised to call this service
	private int type; // Service type

	public ServiceInfo(String name, String token, int type) {
		super();
		this.name = name;
		this.token = token;
		this.type = type;
		this.requests=new ConcurrentLinkedQueue<ZMsg>();
		this.workers=new ConcurrentLinkedQueue<WorkerInfo>();
		this.mq_size=0;
		this.serve_at=System.currentTimeMillis();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ConcurrentLinkedQueue<ZMsg> getRequests() {
		return requests;
	}

	public ConcurrentLinkedQueue<WorkerInfo> getWorkers() {
		return workers;
	}

	public long getServe_at() {
		return serve_at;
	}

	public void updateServe_at() {
		this.serve_at = System.currentTimeMillis();
	}

	public long getMq_size() {
		return mq_size;
	}

	public void setMq_size(long mq_size) {
		this.mq_size = mq_size;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

}
