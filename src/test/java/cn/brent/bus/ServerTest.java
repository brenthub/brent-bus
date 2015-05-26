package cn.brent.bus;

import org.junit.BeforeClass;
import org.junit.Test;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public class ServerTest {
	
	static ZMQ.Context context = ZMQ.context(1);
	static ZMQ.Socket socket = context.socket(ZMQ.REQ);
	
	@BeforeClass
	public static  void init(){
		socket.connect("tcp://localhost:15555"); 
	}
	

	@Test
	public void testClient() throws InterruptedException{
		ZMsg msg=new ZMsg();
		msg.add("getString()");
		msg.addFirst("acc_token");
		msg.addFirst("service2");
		msg.addFirst(Protocol.MDPC);
		msg.send(socket);
		ZMsg res = ZMsg.recvMsg(socket);
		res.dump();
		if (res != null) {
			if (res.size() < 3) {
				throw new BusException("[<MDP>, <status>, <data>] frames required");
			}
			System.out.println(res);
		} 
		
		Thread.sleep(30000L);
	}
	
	@Test
	public void testWorker() throws InterruptedException{
		ZMsg msg=new ZMsg();
		msg.addFirst("1");
		msg.addFirst("acc_token");
		msg.addFirst("reg_token");
		msg.addFirst("service1");
		msg.addFirst(Protocol.MDPW_REG);
		msg.addFirst(Protocol.MDPW);
		msg.send(socket);
		
		Thread.sleep(300000000000L);
	}
	
}
