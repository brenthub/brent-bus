package cn.brent.bus.rpc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMsg;

import cn.brent.bus.BusException;
import cn.brent.bus.worker.WorkHandler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

public class RpcWorkHandler implements WorkHandler {

	private static final Logger logger = LoggerFactory.getLogger(RpcWorkHandler.class);
	
	private Map<String, MethodInstance> methods = new HashMap<String, MethodInstance>();
	
	private String serviceName;
	private String regToken;
	
	public RpcWorkHandler(String serviceName) {
		this(serviceName, null);
	}
	
	public RpcWorkHandler(String serviceName,String regToken) {
		this.serviceName=serviceName;
		this.regToken=regToken;
	}

	public void addVersion(String version, Object service) {
		this.initCommandTable(version, service);
	}
	
	public void addVersion(Object service) {
		this.initCommandTable("", service);
	}
	
	private void initCommandTable(String version, Object service) {
		Class<?>[] classes = new Class<?>[service.getClass().getInterfaces().length + 1];
		classes[0] = service.getClass();
		for (int i = 1; i < classes.length; i++) {
			classes[i] = service.getClass().getInterfaces()[i - 1];
		}
		try {
			for (Class<?> clazz : classes) {
				Method[] methods = clazz.getMethods();
				for (Method m : methods) {
					Remote cmd = m.getAnnotation(Remote.class);
					if (cmd != null) {
						String paramMD5 = "";

						Class<?>[] paramTypes = m.getParameterTypes();
						StringBuilder sb = new StringBuilder();
						for (int i = 0; i < paramTypes.length; i++) {
							Class<?> clz = paramTypes[i];//
							sb.append(clz.getCanonicalName());
						}
						paramMD5 = sb.toString();

						String method = cmd.id();
						if ("".equals(method)) {
							method = m.getName();
						}
						String key = version + ":" + method + ":" + paramMD5;
						if (this.methods.containsKey(key)) {
							logger.info(version + "." + method + " duplicated");
						} else {
							logger.info("register " + service.getClass().getSimpleName() + "\t" + key);
						}
						m.setAccessible(true);
						this.methods.put(key, new MethodInstance(m, service));
					}
				}
			}
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}

	@Override
	public ZMsg handleRequest(ZMsg request) {
		if (request.size() < 1) {
			ZMsg reply = new ZMsg();
			reply.add("400");
			reply.add("request format error: support at least one frame");
			return reply;
		}
		byte[] jsonBytes = request.pollFirst().getData();
		String encoding = Rpc.DEFAULT_ENCODING;
		if (request.size() > 0) {
			encoding = request.popString();
		}

		try {
			byte[] replyBytes = this.handleJsonRequest(jsonBytes, encoding);
			ZMsg reply = new ZMsg();
			reply.add("200");
			reply.add(replyBytes);
			return reply;
		} catch (Throwable e) {
			ZMsg reply = new ZMsg();
			reply.add("500");
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			reply.add(sw.toString());
			return reply;
		}
	}

	public byte[] handleJsonRequest(byte[] jsonData, String charsetName) {
		long start = System.currentTimeMillis();
		StringBuilder log = new StringBuilder();

		Throwable error = null;
		Object result = null;
		JSONObject req = null;
		String version = "";
		String method = null;
		String reqid = null;
		String clientip = null;
		JSONArray args = null;
		MethodInstance target = null;

		JSONArray paramTypes = null;
		String paramMD5 = "";

		String client = null;
		try {
			String jsonStr = new String(jsonData, charsetName);
			req = (JSONObject) JSON.parse(jsonStr);
		} catch (Exception e) {
			e.printStackTrace();
			error = e;
		}

		if (error == null) {
			try {
				version = req.getString(Rpc.P_VERSION);
				method = req.getString(Rpc.P_METHOD);
				args = req.getJSONArray(Rpc.P_PARAMS);
				reqid = req.getString(Rpc.P_REQID);
				clientip = req.getString(Rpc.P_CLIENT_IP);
				log.append(reqid).append("\t").append(clientip).append("\t");
				log.append(version).append("\t").append(method).append("\t").append(args);
				paramTypes = req.getJSONArray(Rpc.P_PARAMTYPES);
				client = req.getString(Rpc.LANG_KEY);
				if (paramTypes != null) {
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < paramTypes.size(); i++) {
						if (paramTypes.get(i) == null)
							continue;
						sb.append(paramTypes.get(i).toString());
					}
					paramMD5 = sb.toString();
				}
			} catch (Exception e) {
				error = e;
			}
			if (version == null) {
				version = "";
			}
			if (method == null) {
				error = new BusException("missing method name");
			}
		}

		String key = version + ":" + method + ":" + paramMD5;
		if (error == null) {
			if (this.methods.containsKey(key)) {
				target = this.methods.get(key);
			} else {
				String key2 = version + ":" + method;
				if (this.methods.containsKey(key2)) {
					target = this.methods.get(key2);
				} else {
					String msg = String.format("method %s not found", key2);
					error = new BusException(msg);
				}
			}
		}
		Class<?> returnType = null;
		if (error == null) {
			try {
				Class<?>[] types = target.method.getParameterTypes();

				// 添加返回值类型，判断是否为数组
				returnType = target.method.getReturnType();
				if (returnType != null) {
					if (returnType.isArray()) {
						returnType = returnType.getComponentType();
					}
				}

				if (args == null) { // FIX of none parameters
					args = new JSONArray();
				}
				if (types.length == args.size()) {
					Object[] params = new Object[types.length];
					for (int i = 0; i < types.length; i++) {
						if (types[i].getName().equals("java.lang.Class"))
							params[i] = Thread.currentThread().getContextClassLoader()
									.loadClass(args.get(i).toString());
						else
							params[i] = args.getObject(i, types[i]);
					}
					result = target.method.invoke(target.instance, params);
				} else {
					error = new BusException("number of argument not match");
				}
			} catch (Throwable e) {
				e.printStackTrace();
				error = e;
			}
		}
		JSONObject data = new JSONObject();
		if (error == null) {
			data.put("result", result);
		} else {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			if (error.getCause() != null) {
				error = error.getCause();
			}
			error.printStackTrace(pw);
			data.put("error", error);
			data.put("stack_trace", sw.toString());
		}
		// support null value
		byte[] resBytes = null;
		if (Rpc.LANG_JAVA.equals(client)) {
			if (returnType != null) {
				data.put("returnType", returnType);
			}

			resBytes = JSON.toJSONBytes(data, SerializerFeature.WriteMapNullValue, SerializerFeature.WriteClassName);
		} else {
			resBytes = JSON.toJSONBytes(data, SerializerFeature.WriteMapNullValue);
		}
		log.append("\t").append(result).append("\t").append(System.currentTimeMillis() - start);
		logger.debug(log.toString());
		return resBytes;
	}

	@Override
	public String getServiceName() {
		return serviceName;
	}

	@Override
	public Set<String> getTopics() {
		return null;
	}

	@Override
	public String getRegToken() {
		return regToken;
	}

	@Override
	public Mode getMode() {
		return Mode.MODE_LB;
	}

	protected class MethodInstance {
		public Method method;
		public Object instance;

		public MethodInstance(Method method, Object instance) {
			this.method = method;
			this.instance = instance;
		}

		@Override
		public String toString() {
			return "MethodInstance [method=" + method + ", instance=" + instance + "]";
		}

	}
}
