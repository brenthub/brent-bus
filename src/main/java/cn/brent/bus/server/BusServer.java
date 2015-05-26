package cn.brent.bus.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

import com.alibaba.fastjson.JSONObject;

import cn.brent.bus.Protocol;


public class BusServer {
	
	private Logger logger = LoggerFactory.getLogger(BusServer.class);
	
	private static final String BROKER_ID = "BusServer";
	
	private Context ctx;
	private Socket socket;
	private int port;
	private String register_token;
	
	private Map<String, WorkerInfo> workers;
	private Map<String, ServiceInfo> services;
	private Poller poller;
	private int heartbeat_expiry;
	
	public BusServer(Context ctx) {
		this(ctx, 15555);
	}

	public BusServer(Context ctx, int port) {
		if (ctx == null)
			throw new IllegalArgumentException("context is null");
		this.ctx = ctx;
		this.port = port;
		this.init();
	}

	public boolean init() {
		if (this.socket != null) {
			this.socket.close();
		}
		this.workers = new HashMap<String, WorkerInfo>();
		this.services = new HashMap<String, ServiceInfo>();
		this.poller = new ZMQ.Poller(1);
		this.socket = ctx.socket(ZMQ.ROUTER);
		this.socket.setIdentity(BROKER_ID.getBytes());
		String address = String.format("tcp://*:%d", port);
		this.socket.bind(address);
		logger.info("BusServer bind "+address);
		return true;
	}

	public void start(){
		new Thread(new Runnable() {
			@Override
			public void run() {
				processMsg();
			}
		}).start();
	}

	public void processMsg() {
		PollItem pollItem = new PollItem(socket, Poller.POLLIN);
		this.poller.register(pollItem);
		while (true) {
			this.poller.poll();
			if (pollItem.isReadable()) {
				ZMsg msg = ZMsg.recvMsg(pollItem.getSocket());
				if (msg == null) {
					continue;
				}
				
				if (msg.size() < 3) {
					logger.error("[<sender>, <empty>, <MDP>] frames required");
					msg.destroy();
					continue;
				}
				
				logger.debug(msg.toString());

				ZFrame sender = msg.pollFirst();
				String empty = msg.popString();
				String mdp = msg.popString();
				
				if (!"".equals(empty)) {
					sender.destroy();
					msg.destroy();
					continue;
				}

				if (Protocol.MDPC.equals(mdp)) {
					clientProcess(sender, msg);
				} else if (Protocol.MDPW.equals(mdp)) {
					workerProcess(sender, msg);
				} else if (Protocol.MDPX.equals(mdp)){
					routeProcess(sender,msg);
				} else if (Protocol.MDPQ.equals(mdp)){
					queueProcess(sender,msg);
				} else if (Protocol.MDPT.equals(mdp)){
					probeProcess(sender,msg);
				} else if (Protocol.MDPM.equals(mdp)){
					monitorProcess(sender,msg);
				} else{
					msg.destroy();
					sender.destroy();
				}
			}
		}
	}
	
	/**
	 * MDPM 处理
	 * @param sender
	 * @param msg
	 */
	private void monitorProcess(ZFrame sender, ZMsg msg) {
		if(msg.size()<2){
			msg.destroy();
			reply(Protocol.MDPM, sender, "400", "<token>,<command> frame required");
			return;
		}
		String token = msg.popString();
		String cmd = msg.popString();

		if(this.register_token!=null && !register_token.equals(token)){
			reply(Protocol.MDPM, sender, "403", "wrong administrator token");
		}

		if(cmd.equals("ls")){
			monitorLsHandler(sender, msg);
		} else if(cmd.equals("clear")){
			monitorClearHandler(sender, msg);
		} else if(cmd.equals("del")){
			monitorDelHandler(sender, msg);
		} else {
			reply(Protocol.MDPM, sender, "404", "unknown command");
		}
	}

	private void monitorDelHandler(ZFrame sender, ZMsg msg) {
		if(msg.size() != 1){// clear svc
			reply(Protocol.MDPM, sender, "400", "service name required");
			return;
		}
		String service_name=msg.popString();
		ServiceInfo  info = services.get(service_name);
		if(info!=null){
			Iterator<WorkerInfo> inter =  info.getWorkers().iterator();
			while(inter.hasNext()){
				WorkerInfo worker=inter.next();
				ZMsg remsg=new ZMsg();
				remsg.add("service is going to be destroyed by broker");
				workerCommand(worker.getAddress(), Protocol.MDPW_DISC, remsg);
				inter.remove();
			}
			services.remove(service_name);
			reply(Protocol.MDPM, sender, "200", "OK");
		}else{
			reply(Protocol.MDPM, sender, "404", String.format("service( %s ), not found", service_name));
		}
	}

	private void monitorClearHandler(ZFrame sender, ZMsg msg) {
		if(msg.size() != 1){// clear svc
			reply(Protocol.MDPM, sender, "400", "service name required");
			return;
		}
		String service_name=msg.popString();
		ServiceInfo  info = services.get(service_name);
		if(info!=null){
			if(info.getRequests().size()>0){
				for(ZMsg t = dequeRequest(info);t!=null;t = dequeRequest(info)){
					t.destroy();
				}
			}
			reply(Protocol.MDPM, sender, "200", "OK");
		}else{
			reply(Protocol.MDPM, sender, "404", String.format("service( %s ), not found", service_name));
		}
	}

	private void monitorLsHandler(ZFrame sender, ZMsg msg) {
		List<Map<String,Object>> re=new ArrayList<Map<String,Object>>();
		for(ServiceInfo info:services.values()){
			Map<String,Object> t=new HashMap<String, Object>();
			t.put("name", info.getName());
			t.put("type", info.getType());
			t.put("token", info.getToken());
			t.put("mq_size", info.getMq_size());
			t.put("request_size", info.getRequests().size());
			t.put("worker_size", info.getWorkers().size());
			t.put("serve_at", info.getServe_at());
			re.add(t);
		}
		reply(Protocol.MDPM, sender, "200", JSONObject.toJSONString(re));
	}


	/**
	 * MDPT 处理
	 * @param sender
	 * @param msg
	 */
	private void probeProcess(ZFrame sender, ZMsg msg) {
		msg.destroy();
		reply(Protocol.MDPT, sender, null, null);
	}

	/**
	 * MDPQ 处理
	 * @param sender
	 * @param msg
	 */
	private void queueProcess(ZFrame sender, ZMsg msg) {
		if (msg.size() < 3) {
			reply(Protocol.MDPQ, sender, "400", "service, token, peerid required");
			msg.destroy();
			return;
		}
		String service_name = msg.popString();
		String token = msg.popString();

		ServiceInfo service = services.get(service_name);
		if (service==null) {
			reply(Protocol.MDPQ, sender, "404", "service not found");
			msg.destroy();
			return;
		}

		if (service.getToken()!=null && !service.getToken().equals(token)) {
			reply(Protocol.MDPQ, sender, "403", "forbidden, wrong token");
			msg.destroy();
			return;
		}
		enqueRequest(service, msg);
		reply(Protocol.MDPQ, sender, Protocol.REQ_SUCC, null);
		serviceDispatch(service);
	}

	

	/**
	 * Worker处理结果返回（路由给client）
	 * @param sender
	 * @param msg
	 */
	private void routeProcess(ZFrame sender, ZMsg msg) {
		logger.debug(msg.toString());
		msg.send(socket);
	}

	/**
	 * Worker请求处理
	 * @param sender
	 * @param msg
	 */
	private void workerProcess(ZFrame sender, ZMsg msg) {
		if (msg.size() < 1) {
			msg.destroy();
			return;
		}

		String worker_id = sender.strhex();
		WorkerInfo worker = workers.get(worker_id);
		String command = msg.popString();
		if (worker == null) {
			if (Protocol.MDPW_REG.equals(command)) {
				worker = workerRegister(sender, msg);
				if (worker != null) {
					workerWaiting(worker);
				}
			} else {
				logger.debug("synchronize peer({})", worker_id);
				workerCommand(sender, Protocol.MDPW_SYNC, null);
			}
			msg.destroy();
			return;
		}

		if (Protocol.MDPW_HBT.equals(command)) {
			worker.setExpiry(this.heartbeat_expiry+System.currentTimeMillis());
			msg.destroy();
		} else if (Protocol.MDPW_IDLE.equals(command)) {
			if(worker.getService().getType()==Protocol.MODE_LB){
				workerWaiting(worker);
			}
			msg.destroy();
		} else if(Protocol.MDPW_DISC.equals(command)){//收到work异常消息
			workerUnregister(worker);
			msg.destroy();
		} else if(Protocol.MDPW_SUB.equals(command)){
			workerSubscribe(worker,msg);
		}  else if(Protocol.MDPW_UNSUB.equals(command)){
			workerUnsubscribe(worker,msg);
		} else {
			msg.destroy();
		}
	}
	
	/**
	 * Worker取消订阅主题
	 * @param worker
	 * @param msg
	 */
	private void workerUnsubscribe(WorkerInfo worker, ZMsg msg) {
		if(worker.getTopics()==null){
			return;
		}
		while(true){
			String topic=msg.popString();
			if(topic==null){
				break;
			}
			worker.getTopics().remove(topic);
		}
		msg.destroy();
	}

	/**
	 * Worker订阅主题
	 * @param worker
	 * @param msg
	 */
	private void workerSubscribe(WorkerInfo worker, ZMsg msg) {
		if(worker.getTopics()==null){
			worker.setTopics(new HashSet<String>());
		}
		while(true){
			String topic=msg.popString();
			if(topic==null){
				break;
			}
			worker.getTopics().add(topic);
		}
		msg.destroy();
	}
	

	/**
	 * Worker空闲（加入队列）
	 * @param worker
	 */
	private void workerWaiting(WorkerInfo worker) {
		worker.getService().getWorkers().addLast(worker);
		worker.setExpiry(System.currentTimeMillis()+this.heartbeat_expiry);
		serviceDispatch(worker.getService());
	}
	
	/**
	 * Worker停止服务
	 * @param worker
	 */
	private void workerUnregister(WorkerInfo worker) {
		if(worker.getService()!=null){
			worker.getService().getWorkers().remove(worker);
			logger.info("unregister worker({}:{})",worker.getService().getName(),worker.getIdentity());
		}
		workers.remove(worker.getIdentity());
	}
	
	/**
	 * 注册Worker
	 * @param sender
	 * @param msg
	 * @return
	 */
	private WorkerInfo workerRegister(ZFrame sender, ZMsg msg) {
		WorkerInfo worker=null;
		if(msg.size()<4){
			workerDisconnect(sender, "svc_name, reg_token, acc_token, type all required");
			return worker;
		}
		String service_name=msg.popString();
		String register_token=msg.popString();
		String access_token=msg.popString();
		String typestr=msg.popString();
		
		ServiceInfo service=services.get(service_name);
		if(this.register_token!=null&&!this.register_token.equals(register_token.toString())){
			workerDisconnect(sender, "unauthorised, register token not matched");
			return worker;
		}
		
		if(!NumberUtils.isDigits(typestr)){
			workerDisconnect(sender, "type is not digit");
			return worker;
		}
		
		int type = Integer.parseInt(typestr);
		
		if(Protocol.MODE_LB!=type&&Protocol.MODE_PUBSUB!=type){
			workerDisconnect(sender, "type frame wrong");
			return worker;
		}
		
		if(service!=null&&!access_token.equals(service.getToken())){
			workerDisconnect(sender, "access token not matched");
			return worker;
		}
		
		if(service!=null&&type!=service.getType()){
			workerDisconnect(sender, "service type not matched");
			return worker;
		}
		
		if(service==null){
			service=new ServiceInfo(service_name,access_token,type);
			services.put(service_name, service);
			logger.info("register service({})",service_name);
		}
		
		worker=new WorkerInfo(sender);
		logger.info("register worker({}:{})",service.getName(),worker.getIdentity());
		worker.setService(service);
		
		workers.put(worker.getIdentity(), worker);
		
		return worker;
	}
	
	/**
	 * 服务提供者断开
	 * 
	 * @param address
	 * @param reason
	 */
	public void workerDisconnect(ZFrame address, String reason) {
		ZMsg command = new ZMsg();
		command.addLast(reason);
		workerCommand(address, Protocol.MDPW_DISC, command);
	}
	
	/**
	 * Client请求处理
	 * @param sender
	 * @param msg
	 */
	private void clientProcess(ZFrame sender, ZMsg msg) {
		if(msg.size()<2){
			reply(Protocol.MDPC, sender, "400", "service, token required");
			return;
		}
		
		String service_name = msg.popString();
		String token = msg.popString();
		ServiceInfo service=this.services.get(service_name);
		if(service==null){
			reply(Protocol.MDPC, sender,  "404", "service not found");
			return;
		}
		
		if(service.getToken()!=null&&!service.getToken().equals(token)){
			reply(Protocol.MDPC, sender,  "403", "forbidden, wrong token");
			return;
		}
		msg.wrap(sender);
		enqueRequest(service, msg);
		serviceDispatch(service);
	}
	
	
	
	/**
	 * 请求分发
	 * @param service
	 */
	private void serviceDispatch(ServiceInfo service) {
		if (service.getType()==Protocol.MODE_LB) {
			while (service.getWorkers().size()>0 && service.getRequests().size()>0) {
				WorkerInfo  worker =  service.getWorkers().peekFirst();
				ZMsg msg = dequeRequest(service);
				workerCommand(worker.getAddress(),Protocol.MDPW_JOB, msg);
			}
		}else if (service.getType()==Protocol. MODE_PUBSUB) { //pubsub
			while (service.getRequests().size()>0) {
				ZMsg msg = dequeRequest(service);
				if (msg.size() < 1) {
					msg.destroy();
					continue;
				}
				String topic = msg.getFirst().toString();
				for (WorkerInfo worker:service.getWorkers()) {
					if (worker.getTopics()!=null) {
						if (worker.getTopics().contains(topic)) {
							ZMsg msg_copy = msg.duplicate();
							workerCommand(worker.getAddress(), Protocol.MDPW_JOB, msg_copy);
						}
					}
				}
				msg.destroy();
			}
		} else {
			logger.error("service type not support");
		}
	}
	
	/**
	 * 转发到服务提供者
	 * [address,,WORKER,JOB,clientAddress,,arg]
	 * 
	 * @param address
	 * @param cmd
	 * @param args
	 */
	public void workerCommand(ZFrame address, String cmd, ZMsg args) {
		if (args == null) {
			args = new ZMsg();
		}
		args.addFirst(cmd);
		args.addFirst(Protocol.MDPW);
		args.wrap(address.duplicate());
		args.send(socket);
	}
	
	/**
	 * 消息出队
	 * @param service
	 * @return
	 */
	private ZMsg dequeRequest(ServiceInfo service) {
		ZMsg msg=service.getRequests().pop();
		if(msg!=null){
			service.setServe_at(System.currentTimeMillis());
			service.setMq_size(service.getMq_size()-msg.contentSize());
		}
		return msg;
	}
	
	/**
	 * 消息进队
	 * @param service
	 * @param msg
	 */
	private void enqueRequest(ServiceInfo service,ZMsg msg) {
		long size=msg.contentSize();
		service.getRequests().add(msg);
		service.setMq_size(service.getMq_size()+size);
	}
	
	/**
	 * 回复消息
	 * [ MDPC, status, content ]
	 * @param mdpc
	 * @param sender
	 * @param status
	 * @param content
	 */
	private void reply(String mdpc, ZFrame sender, String status, String content) {
		ZMsg msg=new ZMsg();
		msg.wrap(sender.duplicate());//增加地址和empty
		msg.addLast(mdpc);
		if(status!=null){
			msg.addLast(status);
		}
		if(content!=null){
			msg.addLast(content);
		}
		msg.send(socket);
	}
}
