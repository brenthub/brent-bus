package cn.brent.bus.server;

import java.util.Set;

import org.zeromq.ZFrame;

public class WorkerInfo {

	private String identity; // Address hex
	private ZFrame address; // Address frame to route to
	private ServiceInfo service; // Owning service, if known
	private long create_time;
	private String client_ip;
	private long serve_at;
	private long expiry; // Expires at unless heartbeat
	private Set<String> topics;

	public WorkerInfo(ZFrame address,String client_ip) {
		this.address = address;
		this.identity = address.strhex();
		this.client_ip=client_ip;
		this.create_time = System.currentTimeMillis();
	}

	public long getServe_at() {
		return serve_at;
	}

	public void updateServe_at() {
		this.serve_at = System.currentTimeMillis();
	}

	public String getClient_ip() {
		return client_ip;
	}

	public void setClient_ip(String client_ip) {
		this.client_ip = client_ip;
	}

	public long getCreate_time() {
		return create_time;
	}

	public String getIdentity() {
		return identity;
	}

	public void setIdentity(String identity) {
		this.identity = identity;
	}

	public ZFrame getAddress() {
		return address;
	}

	public void setAddress(ZFrame address) {
		this.address = address;
	}

	public ServiceInfo getService() {
		return service;
	}

	public void setService(ServiceInfo service) {
		this.service = service;
	}

	public long getExpiry() {
		return expiry;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public Set<String> getTopics() {
		return topics;
	}

	public void setTopics(Set<String> topics) {
		this.topics = topics;
	}

}
