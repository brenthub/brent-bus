package cn.brent.bus;

public class Protocol {

	/** 消息头-客户端发的同步请求(client) */
	public static final String MDPC = "CLIENT";
	/** 消息头-处理者的响应(Worker)*/
	public static final String MDPW = "WORKER";
	/** 消息头-监控(Monitor)*/
	public static final String MDPM = "MONITOR";
	/** 消息头-路由(Route)*/
	public static final String MDPX = "ROUTE";
	/** 消息头-异步请求(Asyn_queue) */
	public static final String MDPQ = "ASYN";
	/** 消息头-侦测(Probe)*/
	public static final String MDPT = "PROBE";

	/**模式-负载均衡 */
	public static final int MODE_LB = 1;
	/**模式-发布订阅 */
	public static final int MODE_PUBSUB = 2;
	
	/**请求-成功*/
	public static final String REQ_SUCC = "200";
	
	/**Worker-注册*/
	public static final String MDPW_REG = "REG";
	/**Worker-任务(客户端请求)*/
	public static final String MDPW_JOB = "JOB";
	/**Worker-心跳*/
	public static final String MDPW_HBT = "HBT";
	/**Worker-描述*/
	public static final String MDPW_DISC = "DISC";
	/**Worker-请求注册*/
	public static final String MDPW_SYNC = "SYNC";
	/**Worker-空闲*/
	public static final String MDPW_IDLE = "IDLE";
	/**Worker-订阅*/
	public static final String MDPW_SUB =  "SUB";
	/**Worker-取消订阅*/
	public static final String MDPW_UNSUB ="UNSUB";
	
	/**
	 * 心跳间隔（毫秒）
	 */
	public static final int HEARTBEAT_INTERVAL = 2500;
	
	/**
	 * 没有收到心跳而宣布死亡的次数
	 */
	public static final int HEARTBEAT_LIVENESS = 3;
	
}
