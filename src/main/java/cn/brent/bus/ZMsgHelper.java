package cn.brent.bus;

import org.zeromq.ZFrame;
import org.zeromq.ZMsg;

public class ZMsgHelper {

	public static String getString(ZFrame frame){
		if(frame!=null&&frame.getData()!=null){
			return new String(frame.getData());
		}
		return null;
	}
	
	public static void main(String[] args) {
		ZMsg msg=new ZMsg();
		msg.add("dd");
		msg.dump();
	}
	
}
