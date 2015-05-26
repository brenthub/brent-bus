package cn.brent.bus.rpc;

public interface SubsService {

	/**
	 * 
	 * @param obj
	 * @return
	 */
	public boolean publish(Object obj);

	/**
	 * 
	 * @param topic
	 *            发布的消息属于的主题
	 * @param obj
	 * @return
	 */
	public boolean publish(String topic, Object obj);
}
