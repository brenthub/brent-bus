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
import cn.brent.bus.monitor.MonitorServer;
import cn.brent.bus.rpc.RpcWorkHandler;
import cn.brent.bus.rpc.SubsMsgHandler;
import cn.brent.bus.rpc.SubsRpcHandler;
import cn.brent.bus.server.BusServer;
import cn.brent.bus.worker.WorkerContext;

public class MonitorTest {

	@BeforeClass
	public static void init() {
		new BusServer(ZMQ.context(1)).start();
		WorkerContext wc = new WorkerContext(ZMQ.context(1));
		RpcWorkHandler rpc = new RpcWorkHandler("XxxServer");
		rpc.addVersion("test", new XxxServer());
		rpc.addVersion("a", new XxxServer());
		wc.registerWorker(rpc, 3);

		SubsMsgHandler sub = new SubsMsgHandler() {
			@Override
			public void handle(Object arg) {
				System.out.println("#####################" + arg);
			}
		};
		wc.registerWorker(new SubsRpcHandler("tstopic", sub).addTopic("top1"), 3);
	}

	@Test
	public void miTest() {
		Context ctx = ZMQ.context(1);
		ClientPoolConfig config = new ClientPoolConfig();
		config.setMaxTotal(100);
		ClientPool cp = new ClientPool(ctx, new String[]{"localhost:15555"}, 10000, config);

		BusClient client = cp.borrowClient();
		ZMsg m = client.monitor("srvls");
		m.dump();

		ZMsg msg = new ZMsg();
		msg.add("XxxServer");
		m = client.monitor("", "workls", msg);
		m.dump();

		// msg=new ZMsg();
		// msg.add("XxxServer");
		// m=client.monitor("","clear",msg);
		// m.dump();

		// msg=new ZMsg();
		// msg.add("XxxServer");
		// m=client.monitor("","del",msg);
		// m.dump();

	}

	@Test
	public void testWeb() {
		new MonitorServer().start();
		try {
			Thread.sleep(10000000000L);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
