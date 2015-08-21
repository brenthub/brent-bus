package cn.brent.bus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;
import org.zeromq.ZMQ.Context;

import cn.brent.bus.client.ClientPool;
import cn.brent.bus.client.ClientPool.ClientPoolConfig;
import cn.brent.bus.rpc.Remote;
import cn.brent.bus.rpc.Rpc;
import cn.brent.bus.rpc.RpcFactory;
import cn.brent.bus.rpc.RpcWorkHandler;
import cn.brent.bus.rpc.SubsMsgHandler;
import cn.brent.bus.rpc.SubsRpcHandler;
import cn.brent.bus.rpc.SubsService;
import cn.brent.bus.server.BusServer;
import cn.brent.bus.worker.WorkHandler;
import cn.brent.bus.worker.WorkerContext;
import cn.brent.bus.worker.WorkHandler.Mode;

public class RpcTest {
	
	public static class XxxServer{
		@Remote
		public String print(){
			return "hello,World!";
		}
		
		@Remote
		public String hello(String name){
			return "hello,"+name+"!";
		}
	}
	
	public static  interface IXxxServer{
		public String print();
		public String hello(String name);
	}

	@BeforeClass
	public static void init(){
		new BusServer(ZMQ.context(1)).start();
		WorkerContext wc=new WorkerContext(ZMQ.context(1));
		RpcWorkHandler rpc=new RpcWorkHandler("XxxServer");
		rpc.addVersion("test",new XxxServer());
		rpc.addVersion("a",new XxxServer());
		wc.registerWorker(rpc,1);
		
		SubsMsgHandler sub=new SubsMsgHandler() {
			@Override
			public void handle(Object arg) {
				System.out.println("#####################"+arg);
			}
		};
		wc.registerWorker(new SubsRpcHandler("tstopic", sub).addTopic("top1"),1);
	}
	
	@Test
	public void testrpc(){
		Context ctx = ZMQ.context(1);
		ClientPoolConfig config=new ClientPoolConfig();
		ClientPool cp=new ClientPool(ctx, new String[]{"localhost:15555" }, 10000, config);
		Rpc rpc=new Rpc(cp,"XxxServer", "test");
		
		String s=rpc.invoke("hello",new Class[]{String.class},"tom");
		System.out.println(s);
		s=rpc.invoke("print");
		System.out.println(s);
	}
	
	@Test
	public void testRpcFactory(){
		RpcFactory f=new RpcFactory(new String[]{"localhost:15555"});
		IXxxServer x=f.getService(IXxxServer.class, "XxxServer?version=test");
		IXxxServer a=f.getService(IXxxServer.class, "XxxServer?version=a");
		System.out.println(x.print());
		System.out.println(a.hello("Brent"));
	}
	
	@Test
	public void testSubs(){
		RpcFactory f=new RpcFactory(new String[]{"localhost:15555"});
		SubsService a=f.getPubService("tstopic?topic=top1");
		List<String> parm=new ArrayList<String>();
		parm.add("aa");
		a.publish(parm);
		a.publish("aaa");
		a.publish("bbb");
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	
}


