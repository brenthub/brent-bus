package cn.brent.bus.rpc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.brent.bus.client.ClientPool;
import cn.brent.bus.client.ClientPool.ClientPoolConfig;

public class RpcFactory {
	protected final Logger logger = LoggerFactory.getLogger(RpcFactory.class);
	
	protected Constructor<RpcProxy> rpcProxy;
	protected Map<String, RpcProxy> cache = new ConcurrentHashMap<String, RpcProxy>();
	protected ClientPool  clientPool;

	protected String[] brokers;
	protected ClientPoolConfig config;
	protected Random random=new Random();
	
	public RpcFactory(String... brokers) {
		this(null, brokers);
	}
	
	public RpcFactory(ClientPoolConfig config,String... brokers) {
		this.config=config;
		this.brokers=brokers;
		try {
			rpcProxy = RpcProxy.class.getConstructor(new Class[] { Map.class, ClientPool.class });
		} catch (Exception e) {
			logger.error("RpcFactory",e);
		}
		clientPool=new ClientPool(null, brokers, null, config);
	}
	
	/**
	 * 将问号后面的参数解析成map
	 * @param kvstring
	 * @return
	 */
	private Map<String, String> parseKeyValues(String kvstring) {
		Map<String, String> res = new HashMap<String, String>();
		String[] parts = kvstring.split("\\&");
		for (String kv : parts) {
			String[] kvp = kv.split("=");
			String key = kvp[0].trim();
			String val = "";
			if (kvp.length > 1) {
				val = kvp[1].trim();
			}
			res.put(key, val);
		}
		return res;
	}
	
	/**
	 * TRADE?version=1.0
	 *
	 * parameters after ? got default values
	 * 
	 * version=1.0 token= log=true
	 * 
	 * @param api
	 * @param serviceURL
	 */
	@SuppressWarnings("unchecked")
	public <T> T getService(Class<T> api,String serviceUrl){
		Map<String, String> kvs = parseAPI(api, serviceUrl);
		RpcProxy handler = cache.get(serviceUrl);
		Class<T>[] interfaces = new Class[] { api };
		if (handler != null) {
			return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), interfaces, handler);
		}
		
		try {
			handler = rpcProxy.newInstance(kvs, clientPool);
		} catch (Exception e) {
			logger.info("error:",e);
			throw new RuntimeException(e);
		}
		cache.put(serviceUrl, handler);
		return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), interfaces, handler);
	}
	
	/**
	 * tstopic
	 *
	 * parameters after ? got default values
	 * 
	 * topic=top1
	 * 
	 * @param serviceURL
	 * @return
	 */
	public SubsService getPubService(String serviceURL) {
		SubsService service = null;
		Map<String, String> kvs = parseAPI(SubsService.class, serviceURL);
		service = new SubsServiceImpl(kvs, clientPool);
		return service;
	}

	/**
	 * 解析连接地址为配置map
	 * @param api
	 * @param serviceUrl 如TRADE?encoding=utf8
	 * @return
	 */
	private <T> Map<String, String> parseAPI(Class<T> api, String serviceUrl) {
		Map<String, String> kvs = new HashMap<String, String>();
		
		serviceUrl = serviceUrl.trim();

		String[] parts = serviceUrl.split("\\?");
		String serviceName = parts[0].trim();

		if (parts.length > 1) {
			String kvstring = parts[1].trim();
			kvs = parseKeyValues(kvstring);
		}
		kvs.put("serviceName", serviceName);
		String version = kvs.get("version");
		if (version == null) {// use default version
			version = "";
			kvs.put("version", version);
		}
		String topic = kvs.get("topic");
		if (topic == null) {
			topic = "";
			kvs.put("topic", topic);
		}

		return kvs;
	}

}
