package cn.brent.bus.rpc;

import java.lang.reflect.Array;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMsg;

import cn.brent.bus.BusException;
import cn.brent.bus.client.BusClient;
import cn.brent.bus.client.ClientPool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

public class Rpc {
	
	public static final String LANG_JAVA = "java";
	public static final String LANG_KEY = "client";
	public static final String DEFAULT_ENCODING = "UTF-8";

	public static final String P_ID = "id";
	public static final String P_VERSION = "version";
	public static final String P_METHOD = "method";
	public static final String P_PARAMS = "params";
	public static final String P_REQID = "reqid";
	public static final String P_CLIENT_IP = "clientip";
	public static final String P_PARAMTYPES = "paramTypes";

	private static final Logger logger = LoggerFactory.getLogger(Rpc.class);

	private ClientPool clientPool;

	private String token = "";
	private String version = "";
	private String serviceName;

	private String encoding;
	private boolean zbusLog = true;
	
	private Integer timeout;

	public Rpc(ClientPool clientPool,String serviceName, String version,String token) {
		this.clientPool = clientPool;
		this.encoding = DEFAULT_ENCODING;
		this.serviceName=serviceName;
		this.version = version;
		this.token = token;
	}
	
	public Rpc(ClientPool clientPool,String serviceName, String version) {
		this(clientPool,serviceName, version, "");
	}

	public <T> T invoke(String method, Object... args) {
		return invoke(method, null, args);
	}
	
	public void setTimeout(Integer timeout) {
		this.timeout = timeout;
	}

	@SuppressWarnings("unchecked")
	public <T> T invoke(String method,Class<?>[] paramType,  Object... args) {

		JSONObject req = new JSONObject();
		req.put(P_ID, "jsonrpc");
		req.put(P_VERSION, this.version);
		req.put(P_METHOD, method);
		req.put(P_PARAMS, args);

		String reqid = UUID.randomUUID().toString();

		req.put(P_REQID, reqid);
		req.put(P_CLIENT_IP, getLocalIP());

		if (paramType != null&&paramType.length>0) {
			String[] paramTypes = new String[args.length];
			for (int i = 0; i < paramType.length; i++) {
				paramTypes[i] = paramType[i].getCanonicalName();
			}
			req.put(P_PARAMTYPES, paramTypes);
		}

		req.put(LANG_KEY, LANG_JAVA);
		ZMsg msg = new ZMsg();
		msg.add(JSON.toJSONBytes(req, SerializerFeature.WriteMapNullValue, SerializerFeature.WriteClassName));

		if (!DEFAULT_ENCODING.equalsIgnoreCase(this.encoding)) {
			msg.add(this.encoding);
		}

		BusClient client = null;
		long ellapsed = -1;
		long start = System.currentTimeMillis();
		String logMsg = "[" + reqid + "]";
		try {
			if (this.zbusLog) {
				String request = new String(msg.getFirst().getData(), this.encoding);
				logMsg += "[REQ] " + request;
			}
			client = this.clientPool.borrowClient();
			if(timeout!=null){
				client.setTimeout(timeout);
			}
			msg = client.request(this.serviceName, this.token, msg);
		} catch (Exception e) {
			logger.debug("error:",e);
			throw new BusException(e.getMessage());
		} finally {
			ellapsed = System.currentTimeMillis() - start;
			if (client != null) {
				clientPool.returnClient(client);
			}
		}
		if (msg == null) {
			throw new BusException("json rpc request timeout");
		}
		if (msg.size() != 2) {
			throw new BusException("json rpc format error[2 frames required]");
		}
		String status = msg.popString();
		if (this.zbusLog) {
			logMsg += "\n      [REP] Cost=" + ellapsed + "(ms), " + "Code=" + status + ", ";
		}
		if (!status.equals("200")) {
			throw new BusException(msg.popString());
		}

		JSONObject res = null;
		String text = "";
		try {
			text = new String(msg.pop().getData(), this.encoding);
			logMsg += "Message=" + text;
			if (this.zbusLog) {
				logger.info(logMsg);
			}
			res = (JSONObject) JSON.parse(text);
		} catch (Exception e) {
			text = text.replace("@type", "unkown-class"); // try disable class
															// feature
			try {
				res = (JSONObject) JSON.parse(text);
			} catch (JSONException ex) {
				new BusException("json error: " + text);
			}
			if (res.containsKey("stack_trace")) {
				logger.info(res.getString("stack_trace"));
			}

			if (res.containsKey("error")) {
				JSONObject error = res.getJSONObject("error");
				if (error.containsKey("message")) {
					throw new BusException(error.getString("message"));
				}
			}
			throw new BusException("json error: " + text);
		}

		if (res.containsKey("result")) {
			if (res.get("result") instanceof JSONArray) {
				String returnType = res.getString("returnType");
				if (returnType != null && returnType.trim().length() > 0) {
					try {
						String result_str = res.getString("result");
						Class<?> clz = Class.forName(returnType);

						if (Collection.class.isAssignableFrom(clz)) {
							return (T) res.get("result");
						} else {
							List<?> resList = JSONArray.parseArray(result_str, clz);
							int size = resList.size();
							T t = (T) Array.newInstance(clz, size);
							System.arraycopy(resList.toArray(), 0, t, 0, size);
							return t;
						}
					} catch (Exception e) {
						return (T) res.get("result");
					}
				} else {
					return (T) res.get("result");
				}
			} else {
				return (T) res.get("result");
			}

		}
		if (res.containsKey("stack_trace")) {
			logger.info(res.getString("stack_trace"));
		}

		if (!res.containsKey("error")) {
			throw new BusException("return json invalid");
		}

		Object error = res.get("error");
		if (error instanceof RuntimeException) { // exception can be loaded
			throw (RuntimeException) error;
		}
		throw new BusException(error.toString());
	}

	private static String ip = null;

	public static String getLocalIP() {
		try {
			if (ip != null) {
				return ip;
			}
			ip = InetAddress.getLocalHost().getHostAddress();
			return ip;
		} catch (Exception e) {
			return "IP_UNKNOWN";
		}
	}

	public void setZbusLog(boolean zbusLog) {
		this.zbusLog = zbusLog;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
	
	

}
