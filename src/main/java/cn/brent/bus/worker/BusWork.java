package cn.brent.bus.worker;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

import cn.brent.bus.BusException;
import cn.brent.bus.Protocol;
import cn.brent.bus.worker.WorkHandler.Mode;

public class BusWork {
	
	private Logger logger=LoggerFactory.getLogger(getClass());

	protected WorkHandler workHandler;
	protected Socket socket; 
	
	protected Set<String> topics;
	protected String name;
	protected String accessToken;
	protected String registerToken;
	protected String mode;
	
	protected boolean registered = false;
	protected boolean stop = false;
	protected long heartbeatAt = System.currentTimeMillis();
	
	public BusWork(Context ctx,String host,int port,String registerToken, WorkHandler workHandler) {
		this.workHandler=workHandler;
		this.registerToken=registerToken;
		
		if(StringUtils.isEmpty(workHandler.getServiceName())){
			throw new BusException("serviceName can't be null");
		}
		
		this.name=workHandler.getServiceName();
		
		if(workHandler.getMode()==null){
			throw new BusException("mode can't be null");
		}
		
		this.mode=workHandler.getMode().getValue().toString();
		
		if(workHandler.getTopics()!=null){
			this.topics=workHandler.getTopics();
		}
		
		if(workHandler.getRegToken()!=null){
			this.accessToken=workHandler.getRegToken();
		}
		this.socket= ctx.socket(ZMQ.DEALER);
		socket.setLinger(0);
		String address = String.format("tcp://%s:%d",host, port);
		socket.connect(address);
		logger.info("connect "+address+" success");
	}
	
	protected void sendHeartbeat(){
		this.sendCommand(Protocol.MDPW_HBT, null);
		this.heartbeatAt = System.currentTimeMillis()+Protocol.HEARTBEAT_INTERVAL;
	}
	
	public void start(){ 
		new Thread(new Runnable() {
			@Override
			public void run() {
				processMsg();
			}
		}).start();
	}
	
	/**
	 * 开始服务
	 */
	public void processMsg(){ 
		if(!this.registered){
			this.register(); 
		}
		this.stop=false;
		while(!stop){
			ZMsg res = ZMsg.recvMsg(this.socket);
			logger.debug(res.toString());
			if( res.size() < 3){
				throw new BusException("[<empty> <header>, <cmd>] frames required");
			}
			
			res.pollFirst(); //empty
			res.pollFirst(); //header
			String cmd = res.popString();
			
			if(Protocol.MDPW_JOB.equals(cmd)){//收到任务
				if(res.size() < 2){
					throw new BusException("[<sock_id> <msg_id>] frames required");
				}
				ZFrame  recvSockId = res.unwrap();
				ZFrame recvMsgId = res.getFirst();
				ZMsg msg = workHandler.handleRequest(res);
				res.destroy();
				if(msg==null||this.workHandler.getMode()!=Mode.MODE_LB){
					continue;
				}
				this.reply(recvSockId,recvMsgId, msg);
				this.sendIdle();//发送空闲指定
			} else if(Protocol.MDPW_DISC.equals(cmd)){ //服务端异常
				throw new BusException(res.popString()); 
			} else if(Protocol.MDPW_SYNC.equals(cmd)){//服务端请求注册
				res.destroy();
				this.register();
			} else {
				throw new BusException("unknown worker command");
			} 
		} 
	}
	
	/**
	 * 答复客户端
	 * @param recvSockId
	 * @param recvMsgId
	 * @param msg
	 * @return
	 */
	protected boolean reply(ZFrame recvSockId, ZFrame recvMsg,  ZMsg msg){ 
		msg.addFirst(recvMsg);
		msg.addFirst(Protocol.REQ_SUCC);
		msg.addFirst(Protocol.MDPC); 
		msg.addFirst("");
		msg.addFirst(recvSockId); 
		msg.addFirst(Protocol.MDPX);
		msg.addFirst("");
		logger.debug(msg.toString());
		return msg.send(this.socket); 
	}
	
	/**
	 * 空闲此服务
	 */
	protected void sendIdle(){
		this.sendCommand(Protocol.MDPW_IDLE, null);
	}
	
	/**
	 * 发送命令
	 * @param cmd
	 * @param args
	 * @return
	 */
	protected boolean sendCommand(String cmd, ZMsg args){
		if(args == null){
			args = new ZMsg();
		}
		args.addFirst(cmd); 
		args.addFirst(Protocol.MDPW);
		args.addFirst("");
		return args.send(this.socket);
	}
	
	/**
	 * 注册服务
	 */
	protected void register(){
		ZMsg msg = new ZMsg();
		msg.addLast(getName());
		msg.addLast(this.registerToken);
		msg.addLast(this.accessToken);
		msg.addLast(this.mode);
		this.sendCommand(Protocol.MDPW_REG, msg);
		this.registered=true;
		
		if(this.topics != null){
			this.subscribe(this.topics.toArray(new String[]{}));
		}
	}
	
	/**
	 * 订阅主题
	 * @param topic
	 * @return
	 */
	public boolean subscribe(String... topic){
		if(!this.registered){
			this.register();
			this.registered = true;
		}
		if(this.topics == null){
			this.topics = new HashSet<String>();
		}
		ZMsg args = new ZMsg();
		for(String t : topic){
			this.topics.add(t);
			args.addLast(t);
		}
		return this.sendCommand(Protocol.MDPW_SUB, args);
	}
	
	/**
	 * 取消定阅
	 * @param topic
	 * @return
	 */
	public boolean unsubscribe(String... topic){
		if(!this.registered){
			this.register();
			this.registered = true;
		}
		if(this.topics == null){
			this.topics = new HashSet<String>();
		}
		ZMsg args = new ZMsg();
		for(String t : topic){
			this.topics.remove(t);
			args.addLast(t);
		}
		return this.sendCommand(Protocol.MDPW_UNSUB, args);
	}
	
	/**
	 * 停止服务
	 */
	public void stop(){
		this.sendCommand(Protocol.MDPW_DISC, null);
		this.stop=true;
	}

	public String getName() {
		return name;
	}

	public long getHeartbeatAt() {
		return heartbeatAt;
	}
	
}
