package cn.brent.bus.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import cn.brent.bus.client.ClientPool;

public class RpcProxy implements InvocationHandler {

	private String serviceUrl;
	private String serviceName;
	private String encoding = "UTF8";
	private String accessToken = "";
	private String version = "";
	private Integer timeout;
	private boolean log = true;

	private ClientPool clientPool;
	private Rpc rpc;

	private static final Object REMOTE_METHOD_CALL = new Object();

	public RpcProxy(Map<String, String> kvs, ClientPool clientPool) {
		this.clientPool = clientPool;
		if (kvs.containsKey("serviceName")) {
			this.serviceName = kvs.get("serviceName");
		} else {
			throw new RuntimeException("serviceName required");
		}

		if (kvs.containsKey("encoding")) {
			this.encoding = kvs.get("encoding");
		}
		if (kvs.containsKey("timeout")) {
			this.timeout = Integer.valueOf(kvs.get("timeout"));
		}
		if (kvs.containsKey("accessToken")) {
			this.accessToken = kvs.get("accessToken");
		}
		if (kvs.containsKey("version")) {
			this.version = kvs.get("version");
		}
		if (kvs.containsKey("log")) {
			this.log = Boolean.valueOf(kvs.get("log"));
		}
		this.rpc = new Rpc(this.clientPool, this.serviceName, version, accessToken);
		if (timeout != null) {
			this.rpc.setTimeout(timeout);
		}
		this.rpc.setEncoding(this.encoding);
		this.rpc.setZbusLog(this.log);
	}

	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Object value = handleLocalMethod(proxy, method, args);
		if (value != REMOTE_METHOD_CALL)
			return value;
		return rpc.invoke(method.getName(), method.getParameterTypes(), args);
	}

	protected Object handleLocalMethod(Object proxy, Method method, Object[] args) throws Throwable {
		String methodName = method.getName();
		Class<?>[] params = method.getParameterTypes();

		if (methodName.equals("equals") && params.length == 1 && params[0].equals(Object.class)) {
			Object value0 = args[0];
			if (value0 == null || !Proxy.isProxyClass(value0.getClass()))
				return new Boolean(false);
			RpcProxy handler = (RpcProxy) Proxy.getInvocationHandler(value0);
			return new Boolean(this.serviceUrl.equals(handler.serviceUrl));
		} else if (methodName.equals("hashCode") && params.length == 0) {
			return new Integer(this.serviceUrl.hashCode());
		} else if (methodName.equals("toString") && params.length == 0) {
			return "BusProxy[" + this.serviceUrl + "]";
		}
		return REMOTE_METHOD_CALL;
	}
}
