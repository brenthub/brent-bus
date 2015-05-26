package cn.brent.bus;

import java.util.HashSet;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMsg;

import cn.brent.bus.client.BusClient;
import cn.brent.bus.client.ClientPool;
import cn.brent.bus.client.ClientPool.ClientPoolConfig;
import cn.brent.bus.server.BusServer;
import cn.brent.bus.worker.WorkHandler;
import cn.brent.bus.worker.WorkerContext;

public class ClientTest {
	
	@BeforeClass
	public static void init(){
		new BusServer(ZMQ.context(1)).start();
		WorkerContext wc=new WorkerContext(ZMQ.context(1));
		wc.registerWorker(new WorkHandler() {
			@Override
			public ZMsg handleRequest(ZMsg request) {
				request.dump();
				ZMsg msg=new ZMsg();
				msg.add("hello,world!!");
				return msg;
			}
			
			@Override
			public Set<String> getTopics() {
				return new HashSet<String>(){{add("topic1");}};
			}
			
			@Override
			public String getServiceName() {
				return "test";
			}
			
			@Override
			public String getRegToken() {
				return "regToken";
			}
			
			@Override
			public Mode getMode() {
				return Mode.MODE_LB;
			}
		});
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
//		wc.unRegisterWorker("test");
	}

	@Test
	public void request() throws InterruptedException{
		Context ctx = ZMQ.context(1);
		BusClient client = new BusClient(ctx);
		ZMsg req = new ZMsg();
		req.add("ls");
		ZMsg res = client.request("test", "regToken", req);
		res.dump();
	}
	
	@Test
	public void pool() throws InterruptedException{
		Context ctx = ZMQ.context(1);
		ClientPoolConfig config=new ClientPoolConfig();
		config.setMaxTotal(100);
		ClientPool cp=new ClientPool(ctx, "localhost", 15555, 10000, config);
		
		BusClient client = cp.borrowClient();
		ZMsg req = new ZMsg();
		req.add("ls");
		ZMsg res = client.request("test", "regToken", req);
		res.dump();
		cp.returnClient(client);
		
		client = cp.borrowClient();
		req = new ZMsg();
		req.add("ls");
		res = client.request("test", "regToken", req);
		res.dump();
		cp.returnClient(client);
	}
}
