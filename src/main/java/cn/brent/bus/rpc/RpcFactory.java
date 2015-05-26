package cn.brent.bus.rpc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.brent.bus.client.ClientPool;

public class RpcFactory {
	private static final Logger logger = LoggerFactory.getLogger(Rpc.class);
	
	private static Constructor<RpcProxy> rpcProxy;
	private static Map<String, RpcProxy> cache = new ConcurrentHashMap<String, RpcProxy>();
	private static Map<String, ClientPool> clientPools = new ConcurrentHashMap<String, ClientPool>();//每个一个服务端分配一个线程池

	static {
		try {
			rpcProxy = RpcProxy.class.getConstructor(new Class[] { Map.class, ClientPool.class });
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}

	/**
	 * 将问号后面的参数解析成map
	 * @param kvstring
	 * @return
	 */
	private static Map<String, String> parseKeyValues(String kvstring) {
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
	 * bus://10.60.60.13:15555/TRADE?encoding=utf8
	 *
	 * parameters after ? got default values
	 * 
	 * encoding=UTF8 module= timeout=10000 token= log=true
	 * 
	 * @param api
	 * @param serviceURL
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getService(Class<T> api, String serviceUrl){
		Map<String, String> kvs = parseAPI(api, serviceUrl);
		String broker = kvs.get("broker");

		RpcProxy handler = cache.get(serviceUrl);
		Class<T>[] interfaces = new Class[] { api };
		if (handler != null) {
			return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), interfaces, handler);
		}
		ClientPool pool = clientPools.get(broker);
		if (pool == null) {
			pool = createClientPool();
			clientPools.put(broker, pool);
		}
		try {
			handler = rpcProxy.newInstance(kvs, pool);
		} catch (Exception e) {
			logger.info("error:",e);
			throw new RuntimeException(e);
		}
		cache.put(serviceUrl, handler);
		return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), interfaces, handler);
	}
	
	public static SubsService getPubService(String serviceURL) {
		SubsService service = null;
		Map<String, String> kvs = parseAPI(SubsService.class, serviceURL);
		String broker = kvs.get("broker");
		ClientPool pool = clientPools.get(broker);
		if (pool == null) {
			pool = createClientPool();
			clientPools.put(broker, pool);
		}
		service = new SubsServiceImpl(kvs, pool);
		return service;
	}
	
	public static ClientPool createClientPool(){
		return new ClientPool();
	}

	/**
	 * 解析连接地址为配置map
	 * @param api
	 * @param serviceUrl 如bus://10.60.60.13:15555/TRADE?encoding=utf8
	 * @return
	 */
	private static <T> Map<String, String> parseAPI(Class<T> api, String serviceUrl) {
		Map<String, String> kvs = new HashMap<String, String>();
		String url = serviceUrl.trim();
		final String prefix = "bus://";
		if (url.startsWith(prefix)) {
			url = serviceUrl.substring(prefix.length());
		}else{
			throw new RuntimeException("url must start with ["+prefix+"]");
		}

		String[] parts = url.split("/");
		String broker = parts[0].trim();
		String service = parts[1].trim();
		parts = service.split("\\?");
		String serviceName = parts[0].trim();

		if (parts.length > 1) {
			String kvstring = parts[1].trim();
			kvs = parseKeyValues(kvstring);
		}
		kvs.put("broker", broker);
		kvs.put("serviceUrl", serviceUrl);
		kvs.put("serviceName", serviceName);
		String module = kvs.get("module");
		if (module == null) {// use default moudle
			module = api.getSimpleName();
			kvs.put("module", module);
		}
		String topic = kvs.get("topic");
		if (topic == null) {
			topic = module;
			kvs.put("topic", topic);
		}

		return kvs;
	}

	public static void destroy() {
		for (Map.Entry<String, ClientPool> entry : clientPools.entrySet()) {
			ClientPool pool = entry.getValue();
			pool.destroy();
		}
	}
}
