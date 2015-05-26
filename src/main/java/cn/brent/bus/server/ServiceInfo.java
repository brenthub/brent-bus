package cn.brent.bus.server;

import java.util.LinkedList;

import org.zeromq.ZMsg;

public class ServiceInfo {
	private String name; // Service name
	private LinkedList<ZMsg> requests; // List of client requests
	private LinkedList<WorkerInfo> workers; // List of waiting workers
	private long serve_at;
	private long mq_size;
	private String token; // Token authorised to call this service
	private int type; // Service type

	public ServiceInfo(String name, String token, int type) {
		super();
		this.name = name;
		this.token = token;
		this.type = type;
		this.requests=new LinkedList<ZMsg>();
		this.workers=new LinkedList<WorkerInfo>();
		this.mq_size=0;
		this.serve_at=System.currentTimeMillis();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LinkedList<ZMsg> getRequests() {
		return requests;
	}

	public void setRequests(LinkedList<ZMsg> requests) {
		this.requests = requests;
	}

	public LinkedList<WorkerInfo> getWorkers() {
		return workers;
	}

	public void setWorkers(LinkedList<WorkerInfo> workers) {
		this.workers = workers;
	}

	public long getServe_at() {
		return serve_at;
	}

	public void setServe_at(long serve_at) {
		this.serve_at = serve_at;
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
