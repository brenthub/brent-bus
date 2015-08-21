package cn.brent.bus.client;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

import cn.brent.bus.BusException;
import cn.brent.bus.Protocol;

/**
 * 总线客户端
 */
public class BusClient {

	private String id;
	
	private String broker = "localhost:15555";
	
	private Context ctx;
	
	private Socket socket;
	
	private Integer timeout=3000;

	public BusClient(Context ctx, String broker) {
		this(ctx, broker, null);
	}

	public BusClient(Context ctx, String broker, String id) {
		if (ctx == null)
			throw new IllegalArgumentException("context is null");
		
		this.ctx = ctx;
		this.broker = broker;
		this.id = id;
		this.reconnect();
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
		if(this.socket!=null){
			this.socket.setReceiveTimeOut(this.timeout);
		}
	}

	public boolean reconnect() {
		if (this.socket != null) {
			this.socket.close();
		}
		this.socket = ctx.socket(ZMQ.DEALER);
		this.socket.setLinger(0);//0表示当socket关闭时丢掉所有未处理的消息
		if(id!=null){
			this.socket.setIdentity(id.getBytes());
		}
		String address = String.format("tcp://%s", broker);
		this.socket.connect(address);
		if(this.timeout!=null){
			this.socket.setReceiveTimeOut(this.timeout);
		}
		return true;
	}

	public ZMsg request(String service, ZMsg message){
		return this.request(service, "", message);
	}
	
	public ZMsg request(String service, String token, ZMsg message) {
		message.addFirst(token);
		message.addFirst(service);
		message.addFirst(Protocol.MDPC);
		message.addFirst("");
		
		message.send(this.socket);
		ZMsg res = ZMsg.recvMsg(this.socket);
		if (res != null) {
			if (res.size() < 3) {
				throw new BusException("[<empty>, <header>, <msg>] frames required");
			}
			res.pollFirst();
			res.pollFirst();
			if(!Protocol.REQ_SUCC.equals(res.popString())){
				throw new BusException(res.popString());
			}
			res.pollFirst();
			return res;
		} else {
			this.reconnect();
			throw new BusException("request timeout");
		}
	}

	/**
	 * 发送异步消息
	 * @param ctrl
	 * @param msg
	 * @return
	 */
	public ZMsg send(String service, String token, ZMsg msg) {
		msg.addFirst(token);
		msg.addFirst(service);
		msg.addFirst(Protocol.MDPQ);
		msg.addFirst("");
		
		msg.send(this.socket);
		ZMsg res = ZMsg.recvMsg(this.socket);
		if (res != null) {
			if (res.size() < 3) {
				throw new BusException("[<empty>, <header>, <msg_id>] frames required");
			}
			res.pollFirst();
			res.pollFirst();
			if(!Protocol.REQ_SUCC.equals(res.popString())){
				throw new BusException(res.popString());
			}
			return res;
		} else {
			this.reconnect();
			throw new BusException("request timeout");
		}
	}

	/**
	 * 发布主题
	 * @param service
	 * @param token
	 * @param topic
	 * @param message
	 * @param timeout
	 * @return
	 */
	public boolean publish(String service, String token, String topic,ZMsg message) {
		message.addFirst(topic);
		ZMsg res = this.send(service,token, message);
		String status = res.popString();
		if (status != null && status.equals("200")) {
			return true;
		}
		return false;
	}

	/**
	 * 自动侦测（当失败时自动重连）
	 * @param probeInterval
	 * @return
	 */
	public void autoProbe(long probeInterval) {
		while (true) {
			int rc = this.probe();
			if (rc != 0) {
				this.reconnect();
			}
			try {
				Thread.sleep(probeInterval);
			} catch (InterruptedException e) {
			}
		}
	}

	/**
	 * 侦测路由服务是否可用
	 * @return 0 成功 -1失败
	 */
	public int probe() {
		ZMsg msg = new ZMsg();
		msg.addFirst(Protocol.MDPT);
		msg.addFirst("");

		msg.send(this.socket);
		ZMsg res = ZMsg.recvMsg(this.socket);
		if (res != null) {
			return 0;
		} else {
			return -1;
		}
	}

	public ZMsg monitor(String cmd) {
		return this.monitor("", cmd, null);
	}
	
	public ZMsg monitor(String token, String cmd,ZMsg message) {
		if(message==null){
			message=new ZMsg();
		}
		message.addFirst(cmd);
		message.addFirst(token);
		message.addFirst(Protocol.MDPM);
		message.addFirst("");

		message.send(this.socket);
		ZMsg res = ZMsg.recvMsg(this.socket);
		if (res != null) {
			if (res.size() < 3) {
				throw new BusException("[<empty>, <header>, <msg_id>] frames required");
			}
			
			res.pollFirst();
			res.pollFirst();
			if(!Protocol.REQ_SUCC.equals(res.popString())){
				throw new BusException(res.popString());
			}
			return res;
		} else {
			this.reconnect();
			throw new BusException("request timeout");
		}
	}

	public void destroy() {
		if (this.socket != null) {
			this.socket.close();
			this.socket = null;
		}
	}

	public String getId() {
		return id;
	}

	public String getBroker() {
		return broker;
	}
	
}
