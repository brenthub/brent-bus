package cn.brent.bus.worker;

import java.util.Set;

import org.zeromq.ZMsg;

import cn.brent.bus.Protocol;

public interface WorkHandler {

	public ZMsg handleRequest(ZMsg request);

	public String getServiceName();

	public Set<String> getTopics();

	public String getRegToken();

	public Mode getMode();

	public static enum Mode {
		/** 模式-负载均衡 */
		MODE_LB(Protocol.MODE_LB),
		/** 模式-发布订阅 */
		MODE_PUBSUB(Protocol.MODE_PUBSUB);

		private int value;

		private Mode(int value) {
			this.value = value;
		}

		public Integer getValue() {
			return value;
		}
	}

}
