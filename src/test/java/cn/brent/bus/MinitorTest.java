package cn.brent.bus;

import org.junit.BeforeClass;
import org.junit.Test;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMsg;

import cn.brent.bus.RpcTest.XxxServer;
import cn.brent.bus.client.BusClient;
import cn.brent.bus.client.ClientPool;
import cn.brent.bus.client.ClientPool.ClientPoolConfig;
import cn.brent.bus.rpc.RpcWorkHandler;
import cn.brent.bus.rpc.SubsMsgHandler;
import cn.brent.bus.rpc.SubsRpcHandler;
import cn.brent.bus.server.BusServer;
import cn.brent.bus.worker.WorkerContext;

public class MinitorTest {

	@BeforeClass
	public static void init(){
		new BusServer(ZMQ.context(1)).start();
		WorkerContext wc=new WorkerContext(ZMQ.context(1));
		RpcWorkHandler rpc=new RpcWorkHandler("XxxServer");
		rpc.addModule("test",new XxxServer());
		rpc.addModule("a",new XxxServer());
		wc.registerWorker(rpc);
		
		SubsMsgHandler sub=new SubsMsgHandler() {
			@Override
			public void handle(Object arg) {
				System.out.println("#####################"+arg);
			}
		};
		wc.registerWorker(new SubsRpcHandler("tstopic", sub).addTopic("top1"));
	}
	
	@Test
	public void miTest(){
		Context ctx = ZMQ.context(1);
		ClientPoolConfig config=new ClientPoolConfig();
		config.setMaxTotal(100);
		ClientPool cp=new ClientPool(ctx, "localhost", 15555, 10000, config);
		
		BusClient client = cp.borrowClient();
		ZMsg m=client.monitor("ls");
		m.dump();
		
		ZMsg msg=new ZMsg();
		msg.add("XxxServer");
		m=client.monitor("","clear",msg);
		m.dump();
		
	}
	
}
